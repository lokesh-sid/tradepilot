package tradingbot.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tradingbot.AbstractIntegrationTest;
import tradingbot.agent.application.event.TradeCompletedEvent;
import tradingbot.agent.infrastructure.persistence.TradeJournalEntity;
import tradingbot.agent.infrastructure.persistence.TradeJournalEntity.Direction;
import tradingbot.agent.infrastructure.persistence.TradeJournalEntity.Outcome;
import tradingbot.agent.infrastructure.persistence.TradeMemoryEntity;
import tradingbot.agent.infrastructure.repository.TradeJournalRepository;
import tradingbot.agent.service.RAGService;
import tradingbot.agent.service.TradeReflectionService;

@DisplayName("Trade reflection listener — async event integration tests")
class TradeReflectionListenerIntegrationTest extends AbstractIntegrationTest {

    private static final String AGENT_ID = "1000000000000003";
    private static final String SYMBOL   = "BTCUSDT";
    private static final String LESSON   = "Entry too early; wait for RSI confirmation before opening long.";

    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TradeJournalService journalService;
    @Autowired private TradeJournalRepository journalRepository;

    @MockitoBean private TradeReflectionService tradeReflectionService;
    @MockitoBean private RAGService ragService;

    @BeforeEach
    void setUp() {
        journalRepository.deleteAll();
        Mockito.reset(tradeReflectionService, ragService);
        when(tradeReflectionService.generateLesson(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(LESSON);
    }

    // -------------------------------------------------------------------------
    // Outcome resolution
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("profitable trade (pnl > 0.05%) completes journal entry with PROFIT outcome")
    void onTradeCompleted_profitTrade_shouldSetProfitOutcomeAndStoreLesson() {
        Long entryId = seedPendingEntry();

        publishEvent(AGENT_ID, SYMBOL, 50_000.0, 52_000.0, 4.0);
        awaitCompletion(entryId);

        TradeJournalEntity updated = journalRepository.findById(entryId).orElseThrow();
        assertThat(updated.getOutcome()).isEqualTo(Outcome.PROFIT);
        assertThat(updated.getExitPrice()).isEqualTo(52_000.0);
        assertThat(updated.getLlmLesson()).isEqualTo(LESSON);
        assertThat(updated.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("losing trade (pnl < -0.05%) completes journal entry with LOSS outcome")
    void onTradeCompleted_lossTrade_shouldSetLossOutcome() {
        Long entryId = seedPendingEntry();

        publishEvent(AGENT_ID, SYMBOL, 50_000.0, 49_000.0, -2.0);
        awaitCompletion(entryId);

        assertThat(journalRepository.findById(entryId).orElseThrow().getOutcome())
                .isEqualTo(Outcome.LOSS);
    }

    @Test
    @DisplayName("breakeven trade (pnl within ±0.05%) completes journal entry with BREAKEVEN outcome")
    void onTradeCompleted_breakevenTrade_shouldSetBreakevenOutcome() {
        Long entryId = seedPendingEntry();

        publishEvent(AGENT_ID, SYMBOL, 50_000.0, 50_020.0, 0.04); // within ±0.05% threshold
        awaitCompletion(entryId);

        assertThat(journalRepository.findById(entryId).orElseThrow().getOutcome())
                .isEqualTo(Outcome.BREAKEVEN);
    }

    // -------------------------------------------------------------------------
    // LLM failure path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("LLM failure uses fallback lesson and still completes the journal entry")
    void onTradeCompleted_llmFailure_shouldCompleteWithFallbackLesson() {
        when(tradeReflectionService.generateLesson(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM API timeout"));

        Long entryId = seedPendingEntry();

        publishEvent(AGENT_ID, SYMBOL, 50_000.0, 49_000.0, -2.0);
        awaitCompletion(entryId);

        TradeJournalEntity updated = journalRepository.findById(entryId).orElseThrow();
        assertThat(updated.getOutcome()).isEqualTo(Outcome.LOSS);
        assertThat(updated.getLlmLesson()).isNotBlank(); // fallback lesson still applied
    }

    // -------------------------------------------------------------------------
    // RAG memory update
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("RAG memory is updated with the correct agent, symbol, and outcome")
    void onTradeCompleted_shouldUpdateRagMemoryAfterCompletion() {
        Long entryId = seedPendingEntry();

        publishEvent(AGENT_ID, SYMBOL, 50_000.0, 52_000.0, 4.0);
        awaitCompletion(entryId);

        verify(ragService).updateTradeReflection(
                eq(AGENT_ID), eq(SYMBOL),
                any(TradeMemoryEntity.Direction.class),
                anyDouble(), anyDouble(),
                any(TradeMemoryEntity.Outcome.class),
                anyDouble(), anyString());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Long seedPendingEntry() {
        return journalService.createEntry(
                AGENT_ID, SYMBOL, Direction.LONG,
                50_000.0, 0.1, null, null, 75, "Original pre-trade reasoning",
            String.valueOf(System.nanoTime()), Instant.now()
        ).getId();
    }

    private void publishEvent(String agentId, String symbol,
                               double entryPrice, double exitPrice, double pnlPercent) {
        double realizedPnl = pnlPercent * entryPrice / 100.0;
        eventPublisher.publishEvent(new TradeCompletedEvent(
                agentId, symbol, TradeMemoryEntity.Direction.LONG,
                entryPrice, exitPrice, pnlPercent, realizedPnl, "Pre-trade reasoning for test"));
    }

    /**
     * Polls the DB until the entry leaves PENDING state, confirming the async
     * {@link TradeReflectionListener} has committed its transaction.
     */
    private void awaitCompletion(Long entryId) {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            TradeJournalEntity entry = journalRepository.findById(entryId).orElseThrow();
            assertThat(entry.getOutcome()).isNotEqualTo(Outcome.PENDING);
        });
    }
}
