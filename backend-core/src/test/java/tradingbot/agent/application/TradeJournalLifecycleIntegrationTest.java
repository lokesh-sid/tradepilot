package tradingbot.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import tradingbot.AbstractIntegrationTest;
import tradingbot.TestIds;
import tradingbot.agent.application.TradeJournalService.JournalStats;
import tradingbot.agent.domain.util.Ids;
import tradingbot.agent.infrastructure.persistence.TradeJournalEntity;
import tradingbot.agent.infrastructure.persistence.TradeJournalEntity.Direction;
import tradingbot.agent.infrastructure.persistence.TradeJournalEntity.Outcome;
import tradingbot.agent.infrastructure.repository.TradeJournalRepository;

@DisplayName("Trade journal — lifecycle integration tests")
class TradeJournalLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final String AGENT_ID = "1000000000000001";
    private static final String SYMBOL   = "BTCUSDT";

    @Autowired private TradeJournalService journalService;
    @Autowired private TradeJournalRepository journalRepository;

    @BeforeEach
    void cleanUp() {
        journalRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // createEntry
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createEntry persists a PENDING entry with correct decision context")
    void createEntry_newTrade_shouldPersistPendingEntry() {
        TradeJournalEntity saved = createEntry(AGENT_ID, SYMBOL, Direction.LONG, 50_000.0);

        assertThat(saved.getId()).isNotNull().isPositive();
        assertThat(saved.getAgentId()).isEqualTo(Ids.requireId(AGENT_ID, "agentId"));
        assertThat(saved.getSymbol()).isEqualTo(SYMBOL);
        assertThat(saved.getDirection()).isEqualTo(Direction.LONG);
        assertThat(saved.getEntryPrice()).isEqualTo(50_000.0);
        assertThat(saved.getOutcome()).isEqualTo(Outcome.PENDING);
        assertThat(saved.getExitPrice()).isNull();
        assertThat(saved.getLlmLesson()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // completeEntry
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("completeEntry on a profit trade sets PROFIT outcome and exit data")
    void completeEntry_profitTrade_shouldSetProfitOutcomeAndExitData() {
        createEntry(AGENT_ID, SYMBOL, Direction.LONG, 50_000.0);

        journalService.completeEntry(AGENT_ID, SYMBOL,
                52_000.0, 200.0, 4.0,
                Outcome.PROFIT, "Entry aligned with momentum; hold longer next time.", Instant.now());

        TradeJournalEntity updated = findFirst(AGENT_ID, SYMBOL);
        assertThat(updated.getOutcome()).isEqualTo(Outcome.PROFIT);
        assertThat(updated.getExitPrice()).isEqualTo(52_000.0);
        assertThat(updated.getPnlPercent()).isEqualTo(4.0);
        assertThat(updated.getLlmLesson()).isNotBlank();
        assertThat(updated.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("completeEntry on a loss trade sets LOSS outcome")
    void completeEntry_lossTrade_shouldSetLossOutcome() {
        createEntry(AGENT_ID, SYMBOL, Direction.SHORT, 50_000.0);

        journalService.completeEntry(AGENT_ID, SYMBOL,
                51_000.0, -50.0, -2.0,
                Outcome.LOSS, "Short entry against trend; wait for confirmation.", Instant.now());

        assertThat(findFirst(AGENT_ID, SYMBOL).getOutcome()).isEqualTo(Outcome.LOSS);
    }

    @Test
    @DisplayName("completeEntry with no PENDING entry is a silent no-op")
    void completeEntry_noPendingEntry_shouldBeNoOp() {
        journalService.completeEntry(AGENT_ID, SYMBOL,
                52_000.0, 200.0, 4.0, Outcome.PROFIT, "lesson", Instant.now());

        assertThat(journalRepository.count()).isZero();
    }

    @Test
    @DisplayName("completeEntry completes the most recent PENDING when multiple exist")
    void completeEntry_multiplePending_shouldCompleteLatest() {
        journalService.createEntry(AGENT_ID, SYMBOL, Direction.LONG,
            48_000.0, 0.1, null, null, 70, "earlier", "1001",
                Instant.now().minusSeconds(300));
        journalService.createEntry(AGENT_ID, SYMBOL, Direction.LONG,
            50_000.0, 0.1, null, null, 80, "later", "1002",
                Instant.now());

        journalService.completeEntry(AGENT_ID, SYMBOL,
                52_000.0, 200.0, 4.0, Outcome.PROFIT, "lesson", Instant.now());

        List<TradeJournalEntity> all = journalRepository.findAll();
        assertThat(all.stream().filter(e -> e.getOutcome() != Outcome.PENDING).count()).isEqualTo(1);
        TradeJournalEntity completed = all.stream()
                .filter(e -> e.getOutcome() != Outcome.PENDING).findFirst().orElseThrow();
        assertThat(completed.getLlmReasoning()).isEqualTo("later");
    }

    // -------------------------------------------------------------------------
    // annotateEntry
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("annotateEntry updates all human-authored fields")
    void annotateEntry_validId_shouldUpdateHumanFields() {
        TradeJournalEntity entry = createEntry(AGENT_ID, SYMBOL, Direction.LONG, 50_000.0);

        Optional<TradeJournalEntity> result = journalService.annotateEntry(
                String.valueOf(entry.getId()),
                "Chased momentum — poor risk/reward",
                List.of("fomo", "late-entry"),
                (short) 3,
                "Would skip this setup in future",
                true);

        assertThat(result).isPresent();
        TradeJournalEntity annotated = result.get();
        assertThat(annotated.getNotes()).isEqualTo("Chased momentum — poor risk/reward");
        assertThat(annotated.getTags()).contains("fomo").contains("late-entry");
        assertThat(annotated.getConviction()).isEqualTo((short) 3);
        assertThat(annotated.getReviewNotes()).isEqualTo("Would skip this setup in future");
        assertThat(annotated.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("annotateEntry returns empty for unknown id")
    void annotateEntry_unknownId_shouldReturnEmpty() {
        Optional<TradeJournalEntity> result = journalService.annotateEntry(
                TestIds.randomNumericIdAsString(), "notes", null, null, null, false);

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // applyBatchReview
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("applyBatchReview stores analysis and flags when flagged=true")
    void applyBatchReview_flaggedTrue_shouldStoreAnalysisAndSetFlag() {
        TradeJournalEntity entry = createEntry(AGENT_ID, SYMBOL, Direction.LONG, 50_000.0);
        String analysis = "Pattern: btcusdt long entries show overconfidence in trending conditions.";

        journalService.applyBatchReview(String.valueOf(entry.getId()), analysis, true);

        TradeJournalEntity reviewed = journalRepository.findById(entry.getId()).orElseThrow();
        assertThat(reviewed.getLlmBatchAnalysis()).isEqualTo(analysis);
        assertThat(reviewed.isFlaggedForReview()).isTrue();
    }

    @Test
    @DisplayName("applyBatchReview stores analysis without flagging when flagged=false")
    void applyBatchReview_flaggedFalse_shouldStoreAnalysisWithoutFlag() {
        TradeJournalEntity entry = createEntry(AGENT_ID, SYMBOL, Direction.LONG, 50_000.0);
        String analysis = "No significant patterns identified.";

        journalService.applyBatchReview(String.valueOf(entry.getId()), analysis, false);

        TradeJournalEntity reviewed = journalRepository.findById(entry.getId()).orElseThrow();
        assertThat(reviewed.getLlmBatchAnalysis()).isEqualTo(analysis);
        assertThat(reviewed.isFlaggedForReview()).isFalse();
    }

    // -------------------------------------------------------------------------
    // list()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("list filtered by agentId returns only that agent's entries")
    void list_filterByAgentId_shouldReturnOnlyMatchingAgent() {
        createEntry(AGENT_ID, SYMBOL, Direction.LONG, 50_000.0);
        createEntry("1000000000000002", SYMBOL, Direction.SHORT, 50_000.0);

        Page<TradeJournalEntity> page = journalService.list(AGENT_ID, null, null, null, 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getAgentId()).isEqualTo(Ids.requireId(AGENT_ID, "agentId"));
    }

    @Test
    @DisplayName("list filtered by outcome returns only entries with that outcome")
    void list_filterByOutcome_shouldReturnOnlyMatchingOutcome() {
        createEntry(AGENT_ID, SYMBOL, Direction.LONG, 50_000.0);
        journalService.completeEntry(AGENT_ID, SYMBOL, 52_000.0, 200.0, 4.0,
                Outcome.PROFIT, "lesson", Instant.now());

        Page<TradeJournalEntity> profits = journalService.list(null, null, "PROFIT", null, 0, 20);
        Page<TradeJournalEntity> losses  = journalService.list(null, null, "LOSS",   null, 0, 20);

        assertThat(profits.getTotalElements()).isEqualTo(1);
        assertThat(losses.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("list filtered by agentId + outcome combines both filters")
    void list_filterByAgentIdAndOutcome_shouldCombineFilters() {
        createEntry(AGENT_ID, SYMBOL, Direction.LONG, 50_000.0);
        journalService.completeEntry(AGENT_ID, SYMBOL, 52_000.0, 200.0, 4.0,
                Outcome.PROFIT, "lesson", Instant.now());
        createEntry("1000000000000002", "ETHUSDT", Direction.SHORT, 3_000.0);
        journalService.completeEntry("1000000000000002", "ETHUSDT", 3_200.0, -60.0, -2.0,
                Outcome.LOSS, "lesson", Instant.now());

        Page<TradeJournalEntity> result = journalService.list(AGENT_ID, null, "PROFIT", null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getAgentId()).isEqualTo(Ids.requireId(AGENT_ID, "agentId"));
        assertThat(result.getContent().get(0).getOutcome()).isEqualTo(Outcome.PROFIT);
    }

    @Test
    @DisplayName("list filtered by flaggedForReview returns only flagged entries")
    void list_filterByFlagged_shouldReturnOnlyFlaggedEntries() {
        TradeJournalEntity flagged   = createEntry(AGENT_ID, SYMBOL, Direction.LONG,  50_000.0);
        TradeJournalEntity unflagged = createEntry(AGENT_ID, SYMBOL, Direction.SHORT, 50_000.0);
        journalService.applyBatchReview(String.valueOf(flagged.getId()),   "analysis mentioning btcusdt long", true);
        journalService.applyBatchReview(String.valueOf(unflagged.getId()), "general analysis", false);

        Page<TradeJournalEntity> result = journalService.list(null, null, null, true, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(flagged.getId());
    }

    // -------------------------------------------------------------------------
    // getStats()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getStats computes total trades, win rate, and average PnL correctly")
    void getStats_mixedOutcomes_shouldComputeCorrectAggregates() {
        // 2 profits, 1 loss
        createAndClose(AGENT_ID, SYMBOL, Direction.LONG,  50_000.0, 52_000.0,  4.0, Outcome.PROFIT);
        createAndClose(AGENT_ID, SYMBOL, Direction.LONG,  50_000.0, 51_500.0,  3.0, Outcome.PROFIT);
        createAndClose(AGENT_ID, SYMBOL, Direction.SHORT, 50_000.0, 51_000.0, -2.0, Outcome.LOSS);

        JournalStats stats = journalService.getStats(AGENT_ID);

        assertThat(stats.totalTrades()).isEqualTo(3);
        assertThat(stats.winRate()).isCloseTo(2.0 / 3.0, offset(0.001));
        assertThat(stats.avgPnlPercent()).isCloseTo((4.0 + 3.0 - 2.0) / 3.0, offset(0.001));
    }

    @Test
    @DisplayName("getStats excludes PENDING entries from all aggregates")
    void getStats_pendingEntries_shouldNotAffectAggregates() {
        createAndClose(AGENT_ID, SYMBOL, Direction.LONG, 50_000.0, 52_000.0, 4.0, Outcome.PROFIT);
        createEntry(AGENT_ID, SYMBOL, Direction.SHORT, 50_000.0); // still open

        JournalStats stats = journalService.getStats(AGENT_ID);

        assertThat(stats.totalTrades()).isEqualTo(1);
        assertThat(stats.winRate()).isCloseTo(1.0, offset(0.001));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TradeJournalEntity createEntry(String agentId, String symbol,
                                            Direction direction, double entryPrice) {
        return journalService.createEntry(agentId, symbol, direction,
                entryPrice, 0.1, null, null, 75, "test reasoning",
                String.valueOf(System.currentTimeMillis()), Instant.now());
    }

    private void createAndClose(String agentId, String symbol, Direction direction,
                                 double entryPrice, double exitPrice,
                                 double pnlPct, Outcome outcome) {
        createEntry(agentId, symbol, direction, entryPrice);
        journalService.completeEntry(agentId, symbol,
                exitPrice, pnlPct * entryPrice / 100.0, pnlPct,
                outcome, "lesson", Instant.now());
    }

    private TradeJournalEntity findFirst(String agentId, String symbol) {
        Long agentIdLong = Ids.requireId(agentId, "agentId");
        return journalRepository.findAll().stream()
                .filter(e -> e.getAgentId().equals(agentIdLong) && e.getSymbol().equals(symbol))
                .findFirst().orElseThrow();
    }
}
