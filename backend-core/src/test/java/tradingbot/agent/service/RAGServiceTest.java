package tradingbot.agent.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentGoal;
import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.ReasoningContext;
import tradingbot.agent.domain.model.TradeDirection;
import tradingbot.agent.domain.model.TradeMemory;
import tradingbot.agent.domain.model.TradeOutcome;
import tradingbot.agent.infrastructure.llm.LLMProvider;
import tradingbot.agent.infrastructure.repository.TradeMemoryRepository;

/**
 * Unit tests for RAGService
 * 
 * Tests the RAG (Retrieval-Augmented Generation) service:
 * - Embedding generation
 * - Memory retrieval
 * - Context augmentation
 * - LLM reasoning with RAG
 * - Memory storage
 */
@ExtendWith(MockitoExtension.class)
class RAGServiceTest {
    
    @Mock
    private EmbeddingService embeddingService;
    
    @Mock
    private MemoryStoreService memoryStore;
    
    @Mock
    private ContextBuilder contextBuilder;
    
    @Mock
    private LLMProvider llmProvider;

    @Mock
    private TradeMemoryRepository tradeMemoryRepository;

    private RAGService ragService;
    
    private Agent testAgent;
    private ReasoningContext testContext;
    private Perception testPerception;
    private Reasoning testReasoning;
    private double[] testEmbedding;
    
    @BeforeEach
    void setUp() {
        ragService = new RAGService(
            embeddingService, memoryStore, contextBuilder,
            Optional.of(llmProvider), tradeMemoryRepository);

        // Set configuration values
        ReflectionTestUtils.setField(ragService, "retrievalTopK", 5);
        ReflectionTestUtils.setField(ragService, "similarityThreshold", 0.7);
        ReflectionTestUtils.setField(ragService, "maxAgeDays", 90);
        
        // Create test data
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Maximize profits");
        testAgent = Agent.create("Test Agent", goal, "BTCUSDT", 10000.0, "test-user-id");
        
        testPerception = new Perception(
            "BTCUSDT",
            45000.0,
            "UPTREND",
            "BULLISH",
            1000000.0,
            Instant.now()
        );
        
        testContext = new ReasoningContext(
            goal,
            testPerception,
            "BTCUSDT",
            10000.0,
            1
        );
        
        testReasoning = new Reasoning(
            "Market trending up",
            "Strong momentum",
            "Low risk",
            "BUY",
            85,
            Instant.now()
        );
        
        testEmbedding = new double[]{0.1, 0.2, 0.3, 0.4, 0.5};
    }
    
    @Test
    void testGenerateReasoningWithRAG_Success() {
        // Given
        String scenarioDescription = "BTC uptrend with bullish sentiment";
        List<TradeMemory> similarMemories = createMockMemories();
        
        when(contextBuilder.buildScenarioDescription(any(Perception.class)))
            .thenReturn(scenarioDescription);
        when(embeddingService.embed(scenarioDescription))
            .thenReturn(testEmbedding);
        when(memoryStore.findSimilar(any(), anyString(), anyInt(), anyDouble(), anyInt()))
            .thenReturn(similarMemories);
        when(contextBuilder.buildAugmentedContext(any(ReasoningContext.class), anyList()))
            .thenReturn("Augmented context with memories");
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(testReasoning);
        
        // When
        Reasoning result = ragService.generateReasoningWithRAG(testAgent, testContext);
        
        // Then
        assertNotNull(result);
        assertEquals("BUY", result.getRecommendation());
        assertEquals(85, result.getConfidence());
        verify(embeddingService).embed(scenarioDescription);
        verify(memoryStore).findSimilar(testEmbedding, "BTCUSDT", 5, 0.7, 90);
        verify(llmProvider).generateReasoning(any(ReasoningContext.class));
    }
    
    @Test
    void testGenerateReasoningWithRAG_NoSimilarMemories() {
        // Given
        String scenarioDescription = "BTC scenario";
        
        when(contextBuilder.buildScenarioDescription(any(Perception.class)))
            .thenReturn(scenarioDescription);
        when(embeddingService.embed(scenarioDescription))
            .thenReturn(testEmbedding);
        when(memoryStore.findSimilar(any(), anyString(), anyInt(), anyDouble(), anyInt()))
            .thenReturn(List.of());
        when(contextBuilder.buildAugmentedContext(any(ReasoningContext.class), anyList()))
            .thenReturn("Context with no memories");
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(testReasoning);
        
        // When
        Reasoning result = ragService.generateReasoningWithRAG(testAgent, testContext);
        
        // Then
        assertNotNull(result);
        verify(memoryStore).findSimilar(testEmbedding, "BTCUSDT", 5, 0.7, 90);
        verify(llmProvider).generateReasoning(any(ReasoningContext.class));
    }
    
    @Test
    void testGenerateReasoningWithRAG_FallbackOnError() {
        // Given
        when(contextBuilder.buildScenarioDescription(any(Perception.class)))
            .thenThrow(new RuntimeException("Embedding service error"));
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(testReasoning);
        
        // When
        Reasoning result = ragService.generateReasoningWithRAG(testAgent, testContext);
        
        // Then
        assertNotNull(result);
        assertEquals("BUY", result.getRecommendation());
        // Should have fallen back to basic reasoning
        verify(llmProvider, times(1)).generateReasoning(any(ReasoningContext.class));
    }
    
    @Test
    void testGenerateReasoningWithRAG_PassesCorrectParameters() {
        // Given
        String scenarioDescription = "Test scenario";
        ArgumentCaptor<String> symbolCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> topKCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Double> thresholdCaptor = ArgumentCaptor.forClass(Double.class);
        
        when(contextBuilder.buildScenarioDescription(any(Perception.class)))
            .thenReturn(scenarioDescription);
        when(embeddingService.embed(scenarioDescription))
            .thenReturn(testEmbedding);
        when(memoryStore.findSimilar(
            any(), symbolCaptor.capture(), topKCaptor.capture(), 
            thresholdCaptor.capture(), anyInt()))
            .thenReturn(List.of());
        when(contextBuilder.buildAugmentedContext(any(), anyList()))
            .thenReturn("context");
        when(llmProvider.generateReasoning(any(ReasoningContext.class)))
            .thenReturn(testReasoning);
        
        // When
        ragService.generateReasoningWithRAG(testAgent, testContext);
        
        // Then
        assertEquals("BTCUSDT", symbolCaptor.getValue());
        assertEquals(5, topKCaptor.getValue());
        assertEquals(0.7, thresholdCaptor.getValue());
    }
    
    @Test
    void testStoreTradeMemory_Success() {
        // Given
        String agentId = "agent-123";
        String symbol = "BTCUSDT";
        String scenario = "Bull market scenario";
        double entryPrice = 45000.0;
        double exitPrice = 46000.0;
        
        when(embeddingService.embed(scenario))
            .thenReturn(testEmbedding);
        
        ArgumentCaptor<TradeMemory> memoryCaptor = ArgumentCaptor.forClass(TradeMemory.class);
        
        // When
        ragService.storeTradeMemory(
            agentId,
            symbol,
            scenario,
            TradeDirection.LONG,
            entryPrice,
            exitPrice,
            TradeOutcome.PROFIT,
            2.2,
            "Good entry timing",
            0.5 // networkFee
        );
        
        // Then
        verify(embeddingService).embed(scenario);
        verify(memoryStore).store(memoryCaptor.capture());
        
        TradeMemory captured = memoryCaptor.getValue();
        assertEquals(agentId, captured.getAgentId());
        assertEquals(symbol, captured.getSymbol());
        assertEquals(TradeDirection.LONG, captured.getDirection());
        assertEquals(entryPrice, captured.getEntryPrice());
        assertEquals(exitPrice, captured.getExitPrice());
        assertEquals(TradeOutcome.PROFIT, captured.getOutcome());
        assertEquals(2.2, captured.getProfitPercent());
        assertEquals(0.5, captured.getNetworkFee());
    }
    
    @Test
    void testStoreTradeMemory_HandlesException() {
        // Given
        when(embeddingService.embed(anyString()))
            .thenThrow(new RuntimeException("Embedding failed"));
        
        // When & Then - should not throw exception
        assertDoesNotThrow(() -> ragService.storeTradeMemory(
            "agent-123",
            "BTCUSDT",
            "scenario",
            TradeDirection.LONG,
            45000.0,
            46000.0,
            TradeOutcome.PROFIT,
            1.5,
            "lesson",
            0.0
        ));
    }
    
    @Test
    void testStoreTradeMemory_ProfitTrade() {
        // Given
        when(embeddingService.embed(anyString()))
            .thenReturn(testEmbedding);
        
        ArgumentCaptor<TradeMemory> memoryCaptor = ArgumentCaptor.forClass(TradeMemory.class);
        
        // When
        ragService.storeTradeMemory(
            "agent-123",
            "BTCUSDT",
            "Good entry",
            TradeDirection.LONG,
            44000.0,
            46000.0,
            TradeOutcome.PROFIT,
            4.5,
            "Caught the trend",
            0.0
        );
        
        // Then
        verify(memoryStore).store(memoryCaptor.capture());
        TradeMemory memory = memoryCaptor.getValue();
        assertEquals(TradeOutcome.PROFIT, memory.getOutcome());
        assertEquals(4.5, memory.getProfitPercent());
    }
    
    @Test
    void testStoreTradeMemory_LossTrade() {
        // Given
        when(embeddingService.embed(anyString()))
            .thenReturn(testEmbedding);
        
        ArgumentCaptor<TradeMemory> memoryCaptor = ArgumentCaptor.forClass(TradeMemory.class);
        
        // When
        ragService.storeTradeMemory(
            "agent-456",
            "ETHUSDT",
            "Bad timing",
            TradeDirection.SHORT,
            3000.0,
            3100.0,
            TradeOutcome.LOSS,
            -3.3,
            "Entered too early",
            0.0
        );
        
        // Then
        verify(memoryStore).store(memoryCaptor.capture());
        TradeMemory memory = memoryCaptor.getValue();
        assertEquals(TradeOutcome.LOSS, memory.getOutcome());
        assertEquals(-3.3, memory.getProfitPercent());
        assertEquals("Entered too early", memory.getLessonLearned());
    }
    
    @Test
    void testStoreTradeMemory_LongPosition() {
        // Given
        when(embeddingService.embed(anyString()))
            .thenReturn(testEmbedding);
        
        ArgumentCaptor<TradeMemory> memoryCaptor = ArgumentCaptor.forClass(TradeMemory.class);
        
        // When
        ragService.storeTradeMemory(
            "agent-789",
            "BTCUSDT",
            "Long entry",
            TradeDirection.LONG,
            45000.0,
            47000.0,
            TradeOutcome.PROFIT,
            4.4,
            "Good support level",
            0.0
        );
        
        // Then
        verify(memoryStore).store(memoryCaptor.capture());
        assertEquals(TradeDirection.LONG, memoryCaptor.getValue().getDirection());
    }
    
    @Test
    void testStoreTradeMemory_ShortPosition() {
        // Given
        when(embeddingService.embed(anyString()))
            .thenReturn(testEmbedding);
        
        ArgumentCaptor<TradeMemory> memoryCaptor = ArgumentCaptor.forClass(TradeMemory.class);
        
        // When
        ragService.storeTradeMemory(
            "agent-789",
            "BTCUSDT",
            "Short entry",
            TradeDirection.SHORT,
            46000.0,
            44000.0,
            TradeOutcome.PROFIT,
            4.35,
            "Resisted at key level",
            0.0
        );
        
        // Then
        verify(memoryStore).store(memoryCaptor.capture());
        assertEquals(TradeDirection.SHORT, memoryCaptor.getValue().getDirection());
    }
    
    @Test
    void testIsHealthy_ReturnsTrue() {
        // Given
        when(memoryStore.isHealthy()).thenReturn(true);
        
        // When
        boolean result = ragService.isHealthy();
        
        // Then
        assertTrue(result);
        verify(memoryStore).isHealthy();
    }
    
    @Test
    void testIsHealthy_ReturnsFalse() {
        // Given
        when(memoryStore.isHealthy()).thenReturn(false);
        
        // When
        boolean result = ragService.isHealthy();
        
        // Then
        assertFalse(result);
        verify(memoryStore).isHealthy();
    }
    
    @Test
    void testGenerateReasoningWithRAG_CreatesAugmentedContext() {
        // Given
        List<TradeMemory> memories = createMockMemories();
        
        when(contextBuilder.buildScenarioDescription(any()))
            .thenReturn("scenario");
        when(embeddingService.embed(anyString()))
            .thenReturn(testEmbedding);
        when(memoryStore.findSimilar(any(), anyString(), anyInt(), anyDouble(), anyInt()))
            .thenReturn(memories);
        when(contextBuilder.buildAugmentedContext(any(), anyList()))
            .thenReturn("augmented");
        when(llmProvider.generateReasoning(any()))
            .thenReturn(testReasoning);
        
        // When
        ragService.generateReasoningWithRAG(testAgent, testContext);
        
        // Then
        verify(contextBuilder).buildAugmentedContext(any(ReasoningContext.class), eq(memories));
    }
    
    @Test
    void testGenerateReasoningWithRAG_WithHighSimilarityThreshold() {
        // Given
        ReflectionTestUtils.setField(ragService, "similarityThreshold", 0.9);
        
        when(contextBuilder.buildScenarioDescription(any()))
            .thenReturn("scenario");
        when(embeddingService.embed(anyString()))
            .thenReturn(testEmbedding);
        when(memoryStore.findSimilar(any(), anyString(), anyInt(), anyDouble(), anyInt()))
            .thenReturn(List.of());
        when(contextBuilder.buildAugmentedContext(any(), anyList()))
            .thenReturn("context");
        when(llmProvider.generateReasoning(any()))
            .thenReturn(testReasoning);
        
        // When
        ragService.generateReasoningWithRAG(testAgent, testContext);
        
        // Then
        verify(memoryStore).findSimilar(any(), anyString(), anyInt(), eq(0.9), anyInt());
    }
    
    private List<TradeMemory> createMockMemories() {
        TradeMemory memory1 = TradeMemory.builder()
            .id("mem-1")
            .agentId("agent-1")
            .symbol("BTCUSDT")
            .scenarioDescription("Bull market")
            .direction(TradeDirection.LONG)
            .entryPrice(44000.0)
            .exitPrice(46000.0)
            .outcome(TradeOutcome.PROFIT)
            .profitPercent(4.5)
            .lessonLearned("Good trend following")
            .timestamp(Instant.now())
            .embedding(testEmbedding)
            .build();
        
        TradeMemory memory2 = TradeMemory.builder()
            .id("mem-2")
            .agentId("agent-1")
            .symbol("BTCUSDT")
            .scenarioDescription("Strong momentum")
            .direction(TradeDirection.LONG)
            .entryPrice(43000.0)
            .exitPrice(45000.0)
            .outcome(TradeOutcome.PROFIT)
            .profitPercent(4.6)
            .lessonLearned("RSI confirmation worked")
            .timestamp(Instant.now())
            .embedding(testEmbedding)
            .build();
        
        return Arrays.asList(memory1, memory2);
    }
}
