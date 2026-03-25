package tradingbot.agent.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import tradingbot.agent.application.event.TradeCompletedEvent;
import tradingbot.agent.infrastructure.persistence.TradeMemoryEntity;
import tradingbot.agent.service.RAGService;
import tradingbot.agent.service.TradeReflectionService;

/**
 * TradeReflectionListener - Async post-trade self-reflection pipeline.
 *
 * When a trade closes, {@link AgentOrchestrator} publishes a {@link TradeCompletedEvent}.
 * This listener picks it up on a background thread (via @Async) so the trading
 * engine is never blocked waiting for an LLM response.
 *
 * The self-reflection pipeline:
 * 1. Calls the LLM via {@link TradeReflectionService} to generate a concise
 *    "lesson learned" from the trade's entry, exit, PnL, and original reasoning.
 * 2. Passes the lesson to {@link RAGService#updateTradeReflection} which:
 *    a. Deletes the stale PENDING vector embedding.
 *    b. Re-embeds the completed trade scenario and stores the updated vector.
 *    c. Updates the SQL metadata record with the real outcome.
 */
@Service
public class TradeReflectionListener {

    private static final Logger logger = LoggerFactory.getLogger(TradeReflectionListener.class);

    private final TradeReflectionService tradeReflectionService;
    private final RAGService ragService;

    public TradeReflectionListener(TradeReflectionService tradeReflectionService,
                                   RAGService ragService) {
        this.tradeReflectionService = tradeReflectionService;
        this.ragService = ragService;
    }

    /**
     * Handle a completed trade event asynchronously.
     *
     * Runs on the shared "AsyncExecutor" thread pool so the main Orchestrator
     * thread is never blocked by the LLM API call.
     */
    @Async
    @EventListener
    public void onTradeCompleted(TradeCompletedEvent event) {
        String agentId = event.getAgentId();
        String symbol = event.getSymbol();

        logger.info("[Reflection] Starting post-trade self-reflection for agent {} on {} (PnL: {}%)",
                agentId, symbol, String.format("%.2f", event.getRealizedPnlPercent()));

        try {
            // Step 1: Ask the LLM for a lesson learned
            String lessonLearned = generateLesson(event);

            logger.info("[Reflection] Agent {} lesson for {}: {}", agentId, symbol, lessonLearned);

            // Step 2: Resolve outcome from PnL
            TradeMemoryEntity.Outcome outcome = resolveOutcome(event.getRealizedPnlPercent());

            // Step 3: Update vector + SQL memory
            ragService.updateTradeReflection(
                    agentId,
                    symbol,
                    event.getDirection(),
                    event.getEntryPrice(),
                    event.getExitPrice(),
                    outcome,
                    event.getRealizedPnlPercent(),
                    lessonLearned
            );

            logger.info("[Reflection] Agent {} memory updated for {} — outcome: {}", agentId, symbol, outcome);

        } catch (Exception e) {
            logger.error("[Reflection] Failed post-trade reflection for agent {} on {}: {}",
                    agentId, symbol, e.getMessage(), e);
            // Non-fatal: failure here must not propagate to the trading thread
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String generateLesson(TradeCompletedEvent event) {
        try {
            return tradeReflectionService.generateLesson(
                    event.getSymbol(),
                    event.getDirection().name(),
                    String.format("%.4f", event.getEntryPrice()),
                    String.format("%.4f", event.getExitPrice()),
                    String.format("%.2f", event.getRealizedPnlPercent()),
                    sanitizeReasoning(event.getOriginalReasoning())
            );
        } catch (Exception e) {
            logger.warn("[Reflection] LLM lesson generation failed for agent {} on {}: {}",
                    event.getAgentId(), event.getSymbol(), e.getMessage());
            // Fallback lesson when the LLM call fails (e.g. API down, rate-limit)
            return event.getRealizedPnlPercent() >= 0
                    ? "Trade closed in profit; review entry timing for future optimization."
                    : "Trade closed at a loss; re-evaluate entry conditions and risk parameters.";
        }
    }

    /**
     * Determine outcome from the realized PnL percentage.
     * Treats values within ±0.05% as breakeven to account for fees.
     */
    private TradeMemoryEntity.Outcome resolveOutcome(double pnlPercent) {
        if (pnlPercent > 0.05) {
            return TradeMemoryEntity.Outcome.PROFIT;
        } else if (pnlPercent < -0.05) {
            return TradeMemoryEntity.Outcome.LOSS;
        } else {
            return TradeMemoryEntity.Outcome.BREAKEVEN;
        }
    }

    /**
     * Trim the original reasoning to a manageable length for the LLM prompt.
     */
    private String sanitizeReasoning(String reasoning) {
        if (reasoning == null || reasoning.isBlank()) {
            return "No pre-trade reasoning recorded.";
        }
        // Keep at most 800 characters to stay within token limits
        return reasoning.length() > 800 ? reasoning.substring(0, 800) + "..." : reasoning;
    }
}
