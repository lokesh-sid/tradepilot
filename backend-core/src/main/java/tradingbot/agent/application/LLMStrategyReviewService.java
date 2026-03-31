package tradingbot.agent.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import tradingbot.agent.infrastructure.persistence.TradeJournalEntity;
import tradingbot.agent.service.StrategyReviewService;

/**
 * LLMStrategyReviewService — scheduled weekly batch analysis of recent trade history.
 *
 * <p>For each agent that has unreviewed closed trades in the lookback window:
 * <ol>
 *   <li>Formats the trade history as a CSV-style prompt.</li>
 *   <li>Calls {@link StrategyReviewService#analyzeTradePatterns} to identify biases
 *       and suggest parameter adjustments.</li>
 *   <li>Stores the analysis in {@code llm_batch_analysis} on each reviewed entry.</li>
 *   <li>Flags entries the LLM singles out as outliers via {@code flagged_for_review}.</li>
 * </ol>
 *
 * <p>All failures are caught and logged — this must never affect live trading.
 *
 * <p>Schedule: every Sunday at 02:00 UTC (configurable via
 * {@code journal.review.cron}, default {@code 0 0 2 * * SUN}).
 *
 * <p>Lookback window: configurable via {@code journal.review.lookback-days},
 * default 30 days.
 */
@Service
public class LLMStrategyReviewService {

    private static final Logger logger = LoggerFactory.getLogger(LLMStrategyReviewService.class);

    private static final int DEFAULT_LOOKBACK_DAYS = 30;

    private final TradeJournalService tradeJournalService;
    private final StrategyReviewService strategyReviewService;

    public LLMStrategyReviewService(
            TradeJournalService tradeJournalService,
            StrategyReviewService strategyReviewService) {
        this.tradeJournalService = tradeJournalService;
        this.strategyReviewService = strategyReviewService;
    }

    /**
     * Runs weekly (Sunday 02:00 UTC by default).
     * Override with {@code journal.review.cron} in application.properties.
     */
    @Scheduled(cron = "${journal.review.cron:0 0 2 * * SUN}")
    public void runWeeklyReview() {
        logger.info("[StrategyReview] Starting weekly batch review");

        Instant since = Instant.now().minus(DEFAULT_LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<TradeJournalEntity> unreviewed = tradeJournalService.findUnreviewedClosed(since);

        if (unreviewed.isEmpty()) {
            logger.info("[StrategyReview] No unreviewed trades in the last {} days", DEFAULT_LOOKBACK_DAYS);
            return;
        }

        // Group by agent
        unreviewed.stream()
                .collect(Collectors.groupingBy(TradeJournalEntity::getAgentId))
                .forEach(this::reviewAgentTrades);

        logger.info("[StrategyReview] Weekly review complete — processed {} entries across {} agents",
                unreviewed.size(),
                unreviewed.stream().map(TradeJournalEntity::getAgentId).distinct().count());
    }

    // -------------------------------------------------------------------------
    // Per-agent review
    // -------------------------------------------------------------------------

    private void reviewAgentTrades(String agentId, List<TradeJournalEntity> trades) {
        logger.info("[StrategyReview] Reviewing {} trades for agent {}", trades.size(), agentId);

        try {
            String tradeHistory = formatTradeHistory(trades);
            String period = formatPeriod(trades);

            String analysis = strategyReviewService.analyzeTradePatterns(agentId, period, tradeHistory);

            // Write the same analysis text to all entries for this batch, and flag
            // any entry where the LLM output references its symbol+direction (simple heuristic).
            for (TradeJournalEntity entry : trades) {
                boolean flagged = isMentioned(analysis, entry);
                tradeJournalService.applyBatchReview(entry.getId(), analysis, flagged);
            }

            logger.info("[StrategyReview] Analysis stored for agent {} ({} entries)", agentId, trades.size());

        } catch (Exception e) {
            logger.error("[StrategyReview] Failed review for agent {}: {}", agentId, e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------

    private String formatTradeHistory(List<TradeJournalEntity> trades) {
        // CSV header + one row per trade
        StringBuilder sb = new StringBuilder(
                "symbol,direction,confidence,entryPrice,exitPrice,pnlPercent,outcome,tags\n");
        for (TradeJournalEntity t : trades) {
            sb.append(String.join(",",
                    t.getSymbol(),
                    t.getDirection() != null ? t.getDirection().name() : "",
                    t.getConfidence() != null ? String.valueOf(t.getConfidence()) : "",
                    String.format("%.4f", t.getEntryPrice()),
                    t.getExitPrice() != null ? String.format("%.4f", t.getExitPrice()) : "",
                    t.getPnlPercent() != null ? String.format("%.2f", t.getPnlPercent()) : "",
                    t.getOutcome() != null ? t.getOutcome().name() : "",
                    t.getTags() != null ? t.getTags() : ""
            )).append('\n');
        }
        return sb.toString();
    }

    private String formatPeriod(List<TradeJournalEntity> trades) {
        Instant earliest = trades.stream()
                .map(TradeJournalEntity::getDecidedAt)
                .min(Instant::compareTo)
                .orElse(Instant.now());
        Instant latest = trades.stream()
                .map(TradeJournalEntity::getDecidedAt)
                .max(Instant::compareTo)
                .orElse(Instant.now());
        return earliest + " to " + latest;
    }

    /**
     * Simple heuristic: flag an entry if the analysis text mentions its symbol
     * and direction explicitly (suggests the LLM called it out specifically).
     */
    private boolean isMentioned(String analysis, TradeJournalEntity entry) {
        if (analysis == null) return false;
        String lower = analysis.toLowerCase();
        String symbol = entry.getSymbol() != null ? entry.getSymbol().toLowerCase() : "";
        String dir = entry.getDirection() != null ? entry.getDirection().name().toLowerCase() : "";
        return !symbol.isEmpty() && lower.contains(symbol) && !dir.isEmpty() && lower.contains(dir);
    }
}
