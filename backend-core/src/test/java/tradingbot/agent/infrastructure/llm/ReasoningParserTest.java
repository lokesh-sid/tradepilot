package tradingbot.agent.infrastructure.llm;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import tradingbot.agent.domain.model.AgentGoal;
import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.ReasoningContext;

class ReasoningParserTest {
    
    @Test
    void testParseCompleteResponse() {
        // Given
        String llmOutput = """
            OBSERVATION: BTC price at $45,000 showing strong upward momentum
            
            ANALYSIS: Technical indicators confirm bullish trend with RSI at 65
            
            RISK ASSESSMENT: Moderate risk due to potential market correction
            
            RECOMMENDATION: BUY
            
            CONFIDENCE: 85%
            """;
        
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Test");
        Perception perception = new Perception("BTCUSDT", 45000.0, "UPTREND", "BULLISH", 1000000.0, Instant.now());
        ReasoningContext context = new ReasoningContext(goal, perception, "BTCUSDT", 10000.0, 1);
        
        // When
        Reasoning result = ReasoningParser.parse(llmOutput, context);
        
        // Then
        assertNotNull(result);
        assertTrue(result.getObservation().contains("BTC price at $45,000"));
        assertTrue(result.getAnalysis().contains("Technical indicators"));
        assertTrue(result.getRiskAssessment().contains("Moderate risk"));
        assertEquals("BUY", result.getRecommendation());
        assertEquals(85, result.getConfidence());
    }
    
    @Test
    void testParseWithDifferentRecommendations() {
        String[] recommendations = {"BUY", "STRONG_BUY", "SELL", "STRONG_SELL", "HOLD"};
        
        for (String recommendation : recommendations) {
            // Given
            String llmOutput = String.format("""
                OBSERVATION: Test observation
                
                ANALYSIS: Test analysis
                
                RISK ASSESSMENT: Test risk
                
                RECOMMENDATION: %s
                
                CONFIDENCE: 70%%
                """, recommendation);
            
            AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Test");
            Perception perception = new Perception("BTCUSDT", 45000.0, "UPTREND", "BULLISH", 1000000.0, Instant.now());
            ReasoningContext context = new ReasoningContext(goal, perception, "BTCUSDT", 10000.0, 1);
            
            // When
            Reasoning result = ReasoningParser.parse(llmOutput, context);
            
            // Then
            assertEquals(recommendation, result.getRecommendation());
        }
    }
    
    @Test
    void testParseWithMissingSections() {
        // Given - incomplete output
        String llmOutput = """
            OBSERVATION: Partial observation
            
            RECOMMENDATION: HOLD
            """;
        
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Test");
        Perception perception = new Perception("BTCUSDT", 45000.0, "UPTREND", "BULLISH", 1000000.0, Instant.now());
        ReasoningContext context = new ReasoningContext(goal, perception, "BTCUSDT", 10000.0, 1);
        
        // When
        Reasoning result = ReasoningParser.parse(llmOutput, context);
        
        // Then - should use defaults for missing sections
        assertNotNull(result);
        assertTrue(result.getObservation().contains("Partial observation"));
        assertNotNull(result.getAnalysis());
        assertNotNull(result.getRiskAssessment());
        assertEquals("HOLD", result.getRecommendation());
    }
    
    @Test
    void testParseWithVariousConfidenceLevels() {
        int[] confidenceLevels = {0, 25, 50, 75, 100};
        
        for (int confidence : confidenceLevels) {
            // Given
            String llmOutput = String.format("""
                OBSERVATION: Test
                ANALYSIS: Test
                RISK ASSESSMENT: Test
                RECOMMENDATION: HOLD
                CONFIDENCE: %d%%
                """, confidence);
            
            AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Test");
            Perception perception = new Perception("BTCUSDT", 45000.0, "UPTREND", "BULLISH", 1000000.0, Instant.now());
            ReasoningContext context = new ReasoningContext(goal, perception, "BTCUSDT", 10000.0, 1);
            
            // When
            Reasoning result = ReasoningParser.parse(llmOutput, context);
            
            // Then
            assertEquals(confidence, result.getConfidence());
        }
    }
    
    @Test
    void testParseWithNoConfidence() {
        // Given - no confidence specified
        String llmOutput = """
            OBSERVATION: Test observation
            ANALYSIS: Test analysis
            RISK ASSESSMENT: Test risk
            RECOMMENDATION: BUY
            """;
        
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Test");
        Perception perception = new Perception("BTCUSDT", 45000.0, "UPTREND", "BULLISH", 1000000.0, Instant.now());
        ReasoningContext context = new ReasoningContext(goal, perception, "BTCUSDT", 10000.0, 1);
        
        // When
        Reasoning result = ReasoningParser.parse(llmOutput, context);
        
        // Then - should default to 50%
        assertEquals(50, result.getConfidence());
    }
}
