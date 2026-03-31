package tradingbot.agent.application;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tradingbot.agent.infrastructure.persistence.TradeJournalEntity;
import tradingbot.agent.infrastructure.persistence.TradeJournalEntity.Direction;
import tradingbot.agent.infrastructure.persistence.TradeJournalEntity.Outcome;
import tradingbot.agent.infrastructure.repository.TradeJournalRepository;

/**
 * TradeJournalService — application service for the trade journal.
 *
 * <p>Coordinates all writes to {@link TradeJournalEntity}:
 * <ul>
 *   <li>{@link #createEntry} — called at position open by {@link TradeExecutionService}</li>
 *   <li>{@link #completeEntry} — called at position close by {@link TradeReflectionListener}</li>
 *   <li>{@link #annotateEntry} — human annotations via the REST API</li>
 *   <li>{@link #applyBatchReview} — written by {@link LLMStrategyReviewService}</li>
 * </ul>
 */
@Service
public class TradeJournalService {

    private static final Logger logger = LoggerFactory.getLogger(TradeJournalService.class);

    private final TradeJournalRepository journalRepository;

    public TradeJournalService(TradeJournalRepository journalRepository) {
        this.journalRepository = journalRepository;
    }

    // -------------------------------------------------------------------------
    // Write — entry time
    // -------------------------------------------------------------------------

    /**
     * Creates a partial journal entry at position open.
     *
     * @param agentId      agent that made the decision
     * @param symbol       trading pair
     * @param direction    LONG or SHORT
     * @param entryPrice   fill price
     * @param quantity     fill quantity
     * @param stopLoss     stop-loss price (may be null)
     * @param takeProfit   take-profit price (may be null)
     * @param confidence   LLM confidence score 0-100 (may be null)
     * @param llmReasoning full reasoning text from AgentDecision (may be null)
     * @param entryOrderId ID of the persisted entry order
     * @param decidedAt    when the decision was produced
     * @return the saved entity
     */
    @Transactional
    public TradeJournalEntity createEntry(
            String agentId,
            String symbol,
            Direction direction,
            double entryPrice,
            double quantity,
            Double stopLoss,
            Double takeProfit,
            Integer confidence,
            String llmReasoning,
            String entryOrderId,
            Instant decidedAt) {

        TradeJournalEntity entity = new TradeJournalEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setAgentId(agentId);
        entity.setSymbol(symbol);
        entity.setDirection(direction);
        entity.setEntryPrice(entryPrice);
        entity.setQuantity(quantity);
        entity.setStopLoss(stopLoss);
        entity.setTakeProfit(takeProfit);
        entity.setConfidence(confidence);
        entity.setLlmReasoning(llmReasoning);
        entity.setEntryOrderId(entryOrderId);
        entity.setDecidedAt(decidedAt);
        entity.setOutcome(Outcome.PENDING);
        entity.setCreatedAt(Instant.now());

        TradeJournalEntity saved = journalRepository.save(entity);
        logger.debug("[Journal] Entry created for agent {} on {} at {}", agentId, symbol, entryPrice);
        return saved;
    }

    // -------------------------------------------------------------------------
    // Write — close time
    // -------------------------------------------------------------------------

    /**
     * Completes the most recent PENDING journal entry for the given agent+symbol.
     *
     * <p>Called by {@link TradeReflectionListener} after lesson generation.
     * No-op if no matching PENDING entry is found (defensive — should not happen).
     *
     * @param agentId      agent ID
     * @param symbol       trading pair
     * @param exitPrice    fill price at close
     * @param realizedPnl  absolute PnL in USD
     * @param pnlPercent   PnL as a percentage of entry notional
     * @param outcome      PROFIT / LOSS / BREAKEVEN
     * @param llmLesson    lesson from {@link tradingbot.agent.service.TradeReflectionService}
     * @param closedAt     close timestamp
     */
    @Transactional
    public void completeEntry(
            String agentId,
            String symbol,
            double exitPrice,
            double realizedPnl,
            double pnlPercent,
            Outcome outcome,
            String llmLesson,
            Instant closedAt) {

        Optional<TradeJournalEntity> opt = journalRepository
                .findTopByAgentIdAndSymbolAndOutcomeOrderByDecidedAtDesc(agentId, symbol, Outcome.PENDING);

        if (opt.isEmpty()) {
            logger.warn("[Journal] No PENDING entry found to complete for agent {} on {}", agentId, symbol);
            return;
        }

        TradeJournalEntity entity = opt.get();
        entity.setExitPrice(exitPrice);
        entity.setRealizedPnl(realizedPnl);
        entity.setPnlPercent(pnlPercent);
        entity.setOutcome(outcome);
        entity.setLlmLesson(llmLesson);
        entity.setClosedAt(closedAt);

        journalRepository.save(entity);
        logger.debug("[Journal] Entry {} completed — outcome: {}, PnL: {}%", entity.getId(), outcome, pnlPercent);
    }

    // -------------------------------------------------------------------------
    // Write — human annotation
    // -------------------------------------------------------------------------

    /**
     * Applies human annotations to an existing journal entry.
     *
     * @param id          journal entry ID
     * @param notes       free-text notes (null = no change)
     * @param tags        tag list (null = no change; empty list = clear tags)
     * @param conviction  pre-trade conviction 1–5 (null = no change)
     * @param reviewNotes post-review reflection (null = no change)
     * @param markReviewed if true, sets {@code reviewedAt} to now
     * @return updated entity, or empty if not found
     */
    @Transactional
    public Optional<TradeJournalEntity> annotateEntry(
            String id,
            String notes,
            List<String> tags,
            Short conviction,
            String reviewNotes,
            boolean markReviewed) {

        return journalRepository.findById(id).map(entity -> {
            if (notes != null)       entity.setNotes(notes);
            if (tags != null)        entity.setTags(tagsToString(tags));
            if (conviction != null)  entity.setConviction(conviction);
            if (reviewNotes != null) entity.setReviewNotes(reviewNotes);
            if (markReviewed)        entity.setReviewedAt(Instant.now());
            return journalRepository.save(entity);
        });
    }

    // -------------------------------------------------------------------------
    // Write — batch review (LLMStrategyReviewService)
    // -------------------------------------------------------------------------

    /**
     * Stores the LLM batch analysis result on a journal entry.
     */
    @Transactional
    public void applyBatchReview(String id, String analysis, boolean flagged) {
        journalRepository.findById(id).ifPresent(entity -> {
            entity.setLlmBatchAnalysis(analysis);
            entity.setFlaggedForReview(flagged);
            journalRepository.save(entity);
        });
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public Optional<TradeJournalEntity> findById(String id) {
        return journalRepository.findById(id);
    }

    public Page<TradeJournalEntity> list(
            String agentId,
            String symbol,
            String outcome,
            Boolean flaggedForReview,
            int page,
            int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "decidedAt"));

        if (agentId != null && symbol != null) {
            return journalRepository.findByAgentIdAndSymbol(agentId, symbol, pageable);
        }
        if (agentId != null && outcome != null) {
            return journalRepository.findByAgentIdAndOutcome(agentId, parseOutcome(outcome), pageable);
        }
        if (agentId != null) {
            return journalRepository.findByAgentId(agentId, pageable);
        }
        if (symbol != null) {
            return journalRepository.findBySymbol(symbol, pageable);
        }
        if (outcome != null) {
            return journalRepository.findByOutcome(parseOutcome(outcome), pageable);
        }
        if (Boolean.TRUE.equals(flaggedForReview)) {
            return journalRepository.findByFlaggedForReview(true, pageable);
        }
        return journalRepository.findAll(pageable);
    }

    public JournalStats getStats(String agentId) {
        long total  = journalRepository.countClosed(agentId, Outcome.PENDING);
        long wins   = journalRepository.countWins(agentId, Outcome.PROFIT);
        Double avgPnl        = journalRepository.avgPnlPercent(agentId, Outcome.PENDING);
        Double avgConfWins   = journalRepository.avgConfidenceOnWins(agentId, Outcome.PROFIT);
        Double avgConfLosses = journalRepository.avgConfidenceOnLosses(agentId, Outcome.LOSS);

        double winRate = total > 0 ? (double) wins / total : 0.0;

        List<TradeJournalEntity> closed = journalRepository.findAllClosedByAgent(agentId, Outcome.PENDING);
        List<TagStats> tagStats = computeTagStats(closed);

        return new JournalStats(total, winRate, avgPnl, avgConfWins, avgConfLosses, tagStats);
    }

    public List<TradeJournalEntity> findUnreviewedClosed(Instant since) {
        return journalRepository.findUnreviewedClosed(since, Outcome.PENDING);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Outcome parseOutcome(String value) {
        try {
            return Outcome.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid outcome filter '" + value
                    + "'. Valid values: PENDING, PROFIT, LOSS, BREAKEVEN, CANCELLED");
        }
    }

    public static List<String> tagsFromString(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String tagsToString(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return String.join(",", tags);
    }

    private List<TagStats> computeTagStats(List<TradeJournalEntity> closed) {
        Map<String, long[]> acc = new LinkedHashMap<>();
        for (TradeJournalEntity e : closed) {
            for (String tag : tagsFromString(e.getTags())) {
                acc.computeIfAbsent(tag, k -> new long[]{0, 0});
                acc.get(tag)[0]++;
                if (e.getOutcome() == Outcome.PROFIT) acc.get(tag)[1]++;
            }
        }
        return acc.entrySet().stream()
                .map(entry -> new TagStats(
                        entry.getKey(),
                        entry.getValue()[0],
                        entry.getValue()[0] > 0 ? (double) entry.getValue()[1] / entry.getValue()[0] : 0.0))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Value objects (used by JournalController)
    // -------------------------------------------------------------------------

    public record JournalStats(
            long totalTrades,
            double winRate,
            Double avgPnlPercent,
            Double avgConfidenceOnWins,
            Double avgConfidenceOnLosses,
            List<TagStats> topTags) {}

    public record TagStats(String tag, long count, double winRate) {}
}
