package tradingbot.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tradingbot.AbstractIntegrationTest;
import tradingbot.TestIds;
import tradingbot.agent.domain.util.Ids;
import tradingbot.agent.infrastructure.persistence.TradeJournalEntity;
import tradingbot.agent.infrastructure.persistence.TradeJournalEntity.Direction;
import tradingbot.agent.infrastructure.persistence.TradeJournalEntity.Outcome;
import tradingbot.agent.infrastructure.repository.TradeJournalRepository;
import tradingbot.agent.service.StrategyReviewService;

// Disable the weekly schedule so it doesn't trigger during tests
@TestPropertySource(properties = "journal.review.cron=-")
@DisplayName("LLM strategy review service — batch review integration tests")
class LLMStrategyReviewServiceIntegrationTest extends AbstractIntegrationTest {

    // Analysis that mentions "btcusdt" and "long" — used to test the flag heuristic
    private static final String ANALYSIS =
            "Pattern: 8 of 10 btcusdt long entries closed at a loss within 2h. " +
            "Suggestion: raise confidence threshold from 70 to 85 for LONG entries.";

    private static final String AGENT_ID = TestIds.randomNumericIdAsString();
    private static final String SYMBOL   = "BTCUSDT";

    @Autowired private LLMStrategyReviewService reviewService;
    @Autowired private TradeJournalService journalService;
    @Autowired private TradeJournalRepository journalRepository;

    @MockitoBean private StrategyReviewService strategyReviewService;

    @BeforeEach
    void setUp() {
        journalRepository.deleteAll();
        Mockito.reset(strategyReviewService);
        when(strategyReviewService.analyzeTradePatterns(anyString(), anyString(), anyString()))
                .thenReturn(ANALYSIS);
    }

    // -------------------------------------------------------------------------
    // Nothing to review
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("no unreviewed closed trades — LLM is not called")
    void runWeeklyReview_noUnreviewedTrades_shouldNotCallLlm() {
        reviewService.runWeeklyReview();

        verify(strategyReviewService, never())
                .analyzeTradePatterns(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("only PENDING trades present — LLM is not called")
    void runWeeklyReview_onlyPendingTrades_shouldNotCallLlm() {
        seedEntry(AGENT_ID, SYMBOL, Direction.LONG, Outcome.PENDING,
                Instant.now().minus(1, ChronoUnit.DAYS));

        reviewService.runWeeklyReview();

        verify(strategyReviewService, never())
                .analyzeTradePatterns(anyString(), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Analysis applied to closed trades
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("closed unreviewed trades — analysis is stored on every entry for that agent")
    void runWeeklyReview_withClosedTrades_shouldApplyAnalysisToAllEntries() {
        seedEntry(AGENT_ID, SYMBOL, Direction.LONG,  Outcome.PROFIT,
                Instant.now().minus(1, ChronoUnit.DAYS));
        seedEntry(AGENT_ID, SYMBOL, Direction.SHORT, Outcome.LOSS,
                Instant.now().minus(2, ChronoUnit.DAYS));

        reviewService.runWeeklyReview();

        assertThat(journalRepository.findAll())
                .allSatisfy(e -> assertThat(e.getLlmBatchAnalysis()).isEqualTo(ANALYSIS));
    }

    @Test
    @DisplayName("multiple agents — each agent's trades are reviewed independently")
    void runWeeklyReview_multipleAgents_shouldReviewEachAgentSeparately() {
        seedEntry(AGENT_ID,     SYMBOL,   Direction.LONG,  Outcome.PROFIT,
                Instant.now().minus(1, ChronoUnit.DAYS));
        seedEntry("1000000000000005", "ETHUSDT", Direction.SHORT, Outcome.LOSS,
                Instant.now().minus(1, ChronoUnit.DAYS));

        reviewService.runWeeklyReview();

        verify(strategyReviewService, times(2))
                .analyzeTradePatterns(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("already-reviewed entries are skipped — llmBatchAnalysis not overwritten")
    void runWeeklyReview_alreadyReviewed_shouldBeSkipped() {
        TradeJournalEntity entry = seedEntry(AGENT_ID, SYMBOL, Direction.LONG, Outcome.PROFIT,
                Instant.now().minus(1, ChronoUnit.DAYS));
        journalService.applyBatchReview(String.valueOf(entry.getId()), "prior analysis", false);

        reviewService.runWeeklyReview();

        verify(strategyReviewService, never())
                .analyzeTradePatterns(anyString(), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Flagging heuristic
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("entry is flagged when analysis mentions its symbol AND direction")
    void runWeeklyReview_analysisContainsSymbolAndDirection_shouldFlagEntry() {
        // ANALYSIS contains "btcusdt" and "long" — matches this BTCUSDT LONG entry
        seedEntry(AGENT_ID, SYMBOL, Direction.LONG, Outcome.LOSS,
                Instant.now().minus(1, ChronoUnit.DAYS));

        reviewService.runWeeklyReview();

        assertThat(journalRepository.findAll().get(0).isFlaggedForReview()).isTrue();
    }

    @Test
    @DisplayName("entry is NOT flagged when analysis does not mention its symbol+direction pair")
    void runWeeklyReview_analysisDoesNotMentionEntry_shouldNotFlagEntry() {
        // ANALYSIS mentions btcusdt+long, but this entry is ETHUSDT SHORT
        seedEntry(AGENT_ID, "ETHUSDT", Direction.SHORT, Outcome.PROFIT,
                Instant.now().minus(1, ChronoUnit.DAYS));

        reviewService.runWeeklyReview();

        assertThat(journalRepository.findAll().get(0).isFlaggedForReview()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("LLM throws — exception is swallowed and entries remain unmodified")
    void runWeeklyReview_llmThrows_shouldNotPropagateExceptionOrModifyEntries() {
        when(strategyReviewService.analyzeTradePatterns(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM API unavailable"));

        seedEntry(AGENT_ID, SYMBOL, Direction.LONG, Outcome.PROFIT,
                Instant.now().minus(1, ChronoUnit.DAYS));

        reviewService.runWeeklyReview(); // must not throw

        TradeJournalEntity entry = journalRepository.findAll().get(0);
        assertThat(entry.getLlmBatchAnalysis()).isNull();
        assertThat(entry.isFlaggedForReview()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Creates a journal entry with the given outcome. For closed outcomes, calls
     * {@link TradeJournalService#completeEntry} with the provided {@code closedAt}
     * so the entry falls within (or outside) the batch review window as needed.
     */
    private TradeJournalEntity seedEntry(String agentId, String symbol,
                                          Direction direction, Outcome outcome,
                                          Instant closedAt) {
        journalService.createEntry(agentId, symbol, direction,
                50_000.0, 0.1, null, null, 75, "test reasoning",
                String.valueOf(System.nanoTime()), Instant.now());

        if (outcome != Outcome.PENDING) {
            double pnlPct = outcome == Outcome.PROFIT ? 2.0 : -2.0;
            journalService.completeEntry(agentId, symbol,
                    50_000.0 * (1 + pnlPct / 100), pnlPct * 50, pnlPct,
                    outcome, "lesson", closedAt);
        }

        Long agentIdLong = Ids.requireId(agentId, "agentId");
        return journalRepository.findAll().stream()
                .filter(e -> e.getAgentId().equals(agentIdLong)
                        && e.getSymbol().equals(symbol)
                        && e.getDirection() == direction)
                .findFirst().orElseThrow();
    }
}
