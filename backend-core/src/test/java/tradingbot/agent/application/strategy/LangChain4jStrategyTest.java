package tradingbot.agent.application.strategy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentGoal;
import tradingbot.agent.domain.model.TradeDirection;
import tradingbot.agent.domain.model.TradeMemory;
import tradingbot.agent.domain.model.TradeOutcome;
import tradingbot.agent.config.AgentExecutionContext;
import tradingbot.agent.config.ExchangeServiceRegistry;
import tradingbot.agent.service.RAGService;
import tradingbot.agent.service.TradingAgentService;

/**
 * Unit tests for LangChain4jStrategy
 * 
 * Tests the agentic strategy that uses LangChain4j framework:
 * - Autonomous tool invocation
 * - RAG context preparation
 * - Response parsing
 * - Experience storage
 */
@ExtendWith(MockitoExtension.class)
class LangChain4jStrategyTest {
    
    @Mock
    private TradingAgentService tradingAgentService;

    @Mock
    private RAGService ragService;

    @Mock
    private ExchangeServiceRegistry exchangeServiceRegistry;

    @Mock
    private AgentExecutionContext executionContext;

    @InjectMocks
    private LangChain4jStrategy strategy;
    
    private Agent testAgent;
    
    @BeforeEach
    void setUp() {
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Maximize profits");
        testAgent = Agent.create("Agentic Agent", goal, "BTCUSDT", 10000.0, "test-user-id");
        
        // Set RAG enabled by default
        ReflectionTestUtils.setField(strategy, "ragEnabled", true);
        ReflectionTestUtils.setField(strategy, "ragContextLimit", 3);
    }
    
    @Test
    void testGetStrategyName() {
        assertEquals("LangChain4j Agentic", strategy.getStrategyName());
    }
    
    @Test
    void testExecuteIteration_CallsTradingAgentService() {
        // Given
        String mockResponse = "After analyzing the market, I recommend BUY. Confidence: 85%";
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn(mockResponse);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        verify(tradingAgentService).analyzeAndDecide(
            anyString(),
            eq("BTCUSDT"),
            eq(testAgent.getGoal().toString()),
            eq(10000.0),
            anyInt(),
            anyString(),
            anyString()
        );
    }
    
    @Test
    void testExecuteIteration_ParsesAgentResponse() {
        // Given
        String mockResponse = "I analyzed the market using tools. Decision: BUY. Confidence: 92%";
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn(mockResponse);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertNotNull(testAgent.getLastReasoning());
        assertTrue(testAgent.getLastReasoning().getAnalysis().contains("analyzed"));
    }
    
    @Test
    void testExecuteIteration_ExtractsConfidenceFromResponse() {
        // Given
        String mockResponse = "Market analysis complete. Recommendation: BUY. Confidence: 78%";
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn(mockResponse);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertEquals(78, testAgent.getLastReasoning().getConfidence());
    }
    
    @Test
    void testExecuteIteration_DefaultConfidenceWhenNotSpecified() {
        // Given
        String mockResponse = "Market looks good. I recommend buying Bitcoin.";
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn(mockResponse);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertEquals(70, testAgent.getLastReasoning().getConfidence());
    }
    
    @Test
    void testExecuteIteration_BuyRecommendation() {
        // Given
        String mockResponse = "After using getCurrentPrice() and calculateRSI(), I recommend BUY. Confidence: 85%";
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn(mockResponse);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertTrue(testAgent.getLastReasoning().getRecommendation().contains("BUY"));
    }
    
    @Test
    void testExecuteIteration_SellRecommendation() {
        // Given
        String mockResponse = "Market is overbought. Decision: SELL. Confidence: 80%";
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn(mockResponse);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertTrue(testAgent.getLastReasoning().getRecommendation().contains("SELL"));
    }
    
    @Test
    void testExecuteIteration_HoldRecommendation() {
        // Given
        String mockResponse = "Market conditions unclear. Recommendation: HOLD. Confidence: 50%";
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn(mockResponse);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertEquals("HOLD", testAgent.getLastReasoning().getRecommendation());
    }
    
    @Test
    void testExecuteIteration_WithRAGContext() {
        // Given
        ReflectionTestUtils.setField(strategy, "ragEnabled", true);
        
        ArgumentCaptor<String> ragContextCaptor = ArgumentCaptor.forClass(String.class);
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), ragContextCaptor.capture(), anyString()))
            .thenReturn("Decision: BUY");
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        String capturedContext = ragContextCaptor.getValue();
        assertNotNull(capturedContext);
        verify(tradingAgentService).analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString());
    }
    
    @Test
    void testExecuteIteration_WithoutRAGContext() {
        // Given
        ReflectionTestUtils.setField(strategy, "ragEnabled", false);
        
        ArgumentCaptor<String> ragContextCaptor = ArgumentCaptor.forClass(String.class);
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), ragContextCaptor.capture(), anyString()))
            .thenReturn("Decision: BUY");
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        String capturedContext = ragContextCaptor.getValue();
        assertEquals("", capturedContext);
    }
    
    @Test
    void testExecuteIteration_UpdatesAgentState() {
        // Given
        String mockResponse = "Analysis complete. Decision: BUY. Confidence: 88%";
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn(mockResponse);
        
        int initialIterationCount = testAgent.getState().getIterationCount();
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertNotNull(testAgent.getLastReasoning());
        assertTrue(testAgent.getState().getIterationCount() > initialIterationCount);
    }
    
    @Test
    void testExecuteIteration_PassesCorrectSymbol() {
        // Given
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "ETH trading");
        Agent ethAgent = Agent.create("ETH Agent", goal, "ETHUSDT", 5000.0, "test-user-id");
        
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn("Decision: BUY");
        
        // When
        strategy.executeIteration(ethAgent, null);
        
        // Then
        verify(tradingAgentService).analyzeAndDecide(
            anyString(),
            eq("ETHUSDT"),
            anyString(),
            eq(5000.0),
            anyInt(),
            anyString(),
            anyString()
        );
    }
    
    @Test
    void testExecuteIteration_PassesCorrectGoal() {
        // Given
        AgentGoal hedgeGoal = new AgentGoal(AgentGoal.GoalType.HEDGE_RISK, "Risk mitigation");
        Agent hedgeAgent = Agent.create("Hedge Agent", hedgeGoal, "BTCUSDT", 10000.0, "test-user-id");
        
        ArgumentCaptor<String> goalCaptor = ArgumentCaptor.forClass(String.class);
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), goalCaptor.capture(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn("Decision: HOLD");
        
        // When
        strategy.executeIteration(hedgeAgent, null);
        
        // Then
        String capturedGoal = goalCaptor.getValue();
        assertTrue(capturedGoal.contains("HEDGE_RISK"));
    }
    
    @Test
    void testExecuteIteration_PassesCorrectCapital() {
        // Given
        double capital = 25000.0;
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Big account");
        Agent bigAgent = Agent.create("Big Agent", goal, "BTCUSDT", capital, "test-user-id");
        
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn("Decision: BUY");
        
        // When
        strategy.executeIteration(bigAgent, null);
        
        // Then
        verify(tradingAgentService).analyzeAndDecide(
            anyString(),
            anyString(),
            anyString(),
            eq(capital),
            anyInt(),
            anyString(),
            anyString()
        );
    }
    
    @Test
    void testExecuteIteration_HandlesMultipleIterations() {
        // Given
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn("Iteration 1: BUY")
            .thenReturn("Iteration 2: HOLD")
            .thenReturn("Iteration 3: SELL");
        
        // When
        strategy.executeIteration(testAgent, null);
        int iteration1Count = testAgent.getState().getIterationCount();
        
        strategy.executeIteration(testAgent, null);
        int iteration2Count = testAgent.getState().getIterationCount();
        
        strategy.executeIteration(testAgent, null);
        int iteration3Count = testAgent.getState().getIterationCount();
        
        // Then
        assertTrue(iteration2Count > iteration1Count);
        assertTrue(iteration3Count > iteration2Count);
        verify(tradingAgentService, times(3)).analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString());
    }
    
    @Test
    void testExecuteIteration_ParsesConfidenceWithColon() {
        // Given
        String mockResponse = "Analysis: BUY recommended. Confidence: 95%";
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn(mockResponse);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertEquals(95, testAgent.getLastReasoning().getConfidence());
    }
    
    @Test
    void testExecuteIteration_ParsesConfidenceWithSpace() {
        // Given
        String mockResponse = "Analysis: BUY recommended. Confidence 82%";
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn(mockResponse);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertEquals(82, testAgent.getLastReasoning().getConfidence());
    }
    
    @Test
    void testExecuteIteration_HandlesLongResponse() {
        // Given
        String longResponse = """
            I've analyzed the market using multiple tools:
            1. getCurrentPrice() returned $45,000
            2. calculateRSI(14) returned 65 (neutral)
            3. get24HourVolume() shows strong activity
            
            Based on this analysis, I recommend BUY with stop-loss at $44,000
            and take-profit at $47,000.
            
            Confidence: 87%
            """;
        
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn(longResponse);
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        assertEquals(87, testAgent.getLastReasoning().getConfidence());
        assertTrue(testAgent.getLastReasoning().getRecommendation().contains("BUY"));
    }
    
    // ========== RAG Integration Tests ==========
    
    @Test
    void testExecuteIteration_RetrievesSimilarTradesFromRAG() {
        // Given
        ReflectionTestUtils.setField(strategy, "ragEnabled", true);
        
        List<TradeMemory> mockMemories = List.of(
            createMockTradeMemory("BTCUSDT", TradeDirection.LONG, TradeOutcome.PROFIT, 5.2)
        );
        
        when(ragService.retrieveSimilarTrades(anyString(), eq(3)))
            .thenReturn(mockMemories);
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn("Decision: BUY. Confidence: 85%");
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        verify(ragService).retrieveSimilarTrades(contains("BTCUSDT"), eq(3));
    }
    
    @Test
    void testExecuteIteration_PassesRAGContextToAgent() {
        // Given
        ReflectionTestUtils.setField(strategy, "ragEnabled", true);
        
        List<TradeMemory> mockMemories = List.of(
            createMockTradeMemory("BTCUSDT", TradeDirection.LONG, TradeOutcome.PROFIT, 5.2),
            createMockTradeMemory("BTCUSDT", TradeDirection.SHORT, TradeOutcome.LOSS, -3.1)
        );
        
        when(ragService.retrieveSimilarTrades(anyString(), eq(3)))
            .thenReturn(mockMemories);
        
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), contextCaptor.capture(), anyString()))
            .thenReturn("Decision: BUY");
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        String capturedContext = contextCaptor.getValue();
        assertTrue(capturedContext.contains("Historical Trading Experiences"));
        assertTrue(capturedContext.contains("BTCUSDT"));
        assertTrue(capturedContext.contains("LONG") || capturedContext.contains("SHORT"));
        assertTrue(capturedContext.contains("Entry:"));
        assertTrue(capturedContext.contains("Profit:"));
    }
    
    @Test
    void testExecuteIteration_StoresTradeMemoryInRAG() {
        // Given
        ReflectionTestUtils.setField(strategy, "ragEnabled", true);
        
        when(ragService.retrieveSimilarTrades(anyString(), eq(3)))
            .thenReturn(List.of());
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn("Decision: BUY. Confidence: 88%");
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        verify(ragService).storeTradeMemory(
            eq(testAgent.getId().toString()),
            eq("BTCUSDT"),
            contains("BUY decision with 88% confidence"),
            eq(TradeDirection.LONG),
            eq(0.0),
            isNull(),
            eq(TradeOutcome.PENDING),
            isNull(),
            anyString(),
            isNull()
        );
    }
    
    @Test
    void testExecuteIteration_StoresSellDecisionAsShort() {
        // Given
        ReflectionTestUtils.setField(strategy, "ragEnabled", true);
        
        when(ragService.retrieveSimilarTrades(anyString(), eq(3)))
            .thenReturn(List.of());
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn("Decision: SELL. Confidence: 75%");
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        verify(ragService).storeTradeMemory(
            anyString(),
            anyString(),
            anyString(),
            eq(TradeDirection.SHORT),
            anyDouble(),
            isNull(),
            eq(TradeOutcome.PENDING),
            isNull(),
            anyString(),
            isNull()
        );
    }
    
    @Test
    void testExecuteIteration_DoesNotStoreHoldDecision() {
        // Given
        ReflectionTestUtils.setField(strategy, "ragEnabled", true);
        
        when(ragService.retrieveSimilarTrades(anyString(), eq(3)))
            .thenReturn(List.of());
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn("Decision: HOLD. Confidence: 60%");
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        verify(ragService).storeTradeMemory(
            anyString(),
            anyString(),
            anyString(),
            isNull(), // HOLD maps to null direction
            anyDouble(),
            isNull(),
            eq(TradeOutcome.PENDING),
            isNull(),
            anyString(),
            isNull()
        );
    }
    
    @Test
    void testExecuteIteration_RAGDisabled_DoesNotRetrieve() {
        // Given
        ReflectionTestUtils.setField(strategy, "ragEnabled", false);
        
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn("Decision: BUY. Confidence: 80%");
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        verify(ragService, never()).retrieveSimilarTrades(anyString(), anyInt());
    }
    
    @Test
    void testExecuteIteration_RAGDisabled_DoesNotStore() {
        // Given
        ReflectionTestUtils.setField(strategy, "ragEnabled", false);
        
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn("Decision: BUY. Confidence: 80%");
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        verify(ragService, never()).storeTradeMemory(
            anyString(), anyString(), anyString(), any(), anyDouble(), 
            any(), any(), any(), anyString(), any()
        );
    }
    
    @Test
    void testExecuteIteration_RAGException_DoesNotFailExecution() {
        // Given
        ReflectionTestUtils.setField(strategy, "ragEnabled", true);
        
        when(ragService.retrieveSimilarTrades(anyString(), anyInt()))
            .thenThrow(new RuntimeException("Vector DB Connection Failed"));
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn("Decision: BUY");
        
        // When & Then - Should not throw
        assertDoesNotThrow(() -> strategy.executeIteration(testAgent, null));
        
        // Verify it still processes the agent response
        assertNotNull(testAgent.getLastReasoning());
    }
    
    @Test
    void testExecuteIteration_EmptyRAGResults_ShowsNoDataMessage() {
        // Given
        ReflectionTestUtils.setField(strategy, "ragEnabled", true);
        
        when(ragService.retrieveSimilarTrades(anyString(), eq(3)))
            .thenReturn(List.of());
        
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), contextCaptor.capture(), anyString()))
            .thenReturn("Decision: BUY");
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        String capturedContext = contextCaptor.getValue();
        assertTrue(capturedContext.contains("No historical trading data available yet"));
    }
    
    @Test
    void testExecuteIteration_MultipleRAGMemories_FormatsCorrectly() {
        // Given
        ReflectionTestUtils.setField(strategy, "ragEnabled", true);
        
        List<TradeMemory> mockMemories = List.of(
            createMockTradeMemory("BTCUSDT", TradeDirection.LONG, TradeOutcome.PROFIT, 8.5),
            createMockTradeMemory("BTCUSDT", TradeDirection.SHORT, TradeOutcome.PROFIT, 3.2),
            createMockTradeMemory("BTCUSDT", TradeDirection.LONG, TradeOutcome.LOSS, -4.1)
        );
        
        when(ragService.retrieveSimilarTrades(anyString(), eq(3)))
            .thenReturn(mockMemories);
        
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), contextCaptor.capture(), anyString()))
            .thenReturn("Decision: BUY");
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        String capturedContext = contextCaptor.getValue();
        assertTrue(capturedContext.contains("1. BTCUSDT"));
        assertTrue(capturedContext.contains("2. BTCUSDT"));
        assertTrue(capturedContext.contains("3. BTCUSDT"));
        assertTrue(capturedContext.contains("Direction: LONG"));
        assertTrue(capturedContext.contains("Direction: SHORT"));
    }
    
    @Test
    void testExecuteIteration_IncludesGoalInRAGQuery() {
        // Given
        ReflectionTestUtils.setField(strategy, "ragEnabled", true);
        
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        when(ragService.retrieveSimilarTrades(queryCaptor.capture(), eq(3)))
            .thenReturn(List.of());
        when(tradingAgentService.analyzeAndDecide(
            anyString(), anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString()))
            .thenReturn("Decision: BUY");
        
        // When
        strategy.executeIteration(testAgent, null);
        
        // Then
        String query = queryCaptor.getValue();
        assertTrue(query.contains("BTCUSDT"));
        assertTrue(query.contains("MAXIMIZE_PROFIT"));
        assertTrue(query.contains("goal:"));
    }
    
    // ========== Helper Methods ==========
    
    private TradeMemory createMockTradeMemory(String symbol, TradeDirection direction, 
                                              TradeOutcome outcome, double profitPercent) {
        return TradeMemory.builder()
            .id(java.util.UUID.randomUUID().toString())
            .agentId(testAgent.getId().toString())
            .symbol(symbol)
            .scenarioDescription("Test scenario")
            .direction(direction)
            .entryPrice(45000.0)
            .exitPrice(direction == TradeDirection.LONG ? 
                45000.0 * (1 + profitPercent / 100) : 
                45000.0 * (1 - profitPercent / 100))
            .outcome(outcome)
            .profitPercent(profitPercent)
            .lessonLearned("Test lesson learned")
            .timestamp(Instant.now())
            .embedding(new double[384])
            .build();
    }
}
