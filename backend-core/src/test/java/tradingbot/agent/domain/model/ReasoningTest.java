package tradingbot.agent.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ReasoningTest {
    
    @Test
    void testReasoningCreation() {
        // Given
        Instant timestamp = Instant.now();
        
        // When
        Reasoning reasoning = new Reasoning(
            "BTC price at $45000",
            "Strong uptrend confirmed",
            "Low risk, stop loss at $44000",
            "BUY",
            85,
            timestamp
        );
        
        // Then
        assertEquals("BTC price at $45000", reasoning.getObservation());
        assertEquals("Strong uptrend confirmed", reasoning.getAnalysis());
        assertEquals("Low risk, stop loss at $44000", reasoning.getRiskAssessment());
        assertEquals("BUY", reasoning.getRecommendation());
        assertEquals(85, reasoning.getConfidence());
        assertEquals(timestamp, reasoning.getTimestamp());
    }
    
    @Test
    void testConfidenceBounds() {
        // Test upper bound
        Reasoning r1 = new Reasoning("obs", "analysis", "risk", "BUY", 150, Instant.now());
        assertEquals(100, r1.getConfidence()); // Clamped to 100
        
        // Test lower bound
        Reasoning r2 = new Reasoning("obs", "analysis", "risk", "SELL", -50, Instant.now());
        assertEquals(0, r2.getConfidence()); // Clamped to 0
        
        // Test valid value
        Reasoning r3 = new Reasoning("obs", "analysis", "risk", "HOLD", 75, Instant.now());
        assertEquals(75, r3.getConfidence());
    }
    
    @Test
    void testDifferentRecommendations() {
        // Test various recommendations
        String[] recommendations = {"BUY", "STRONG_BUY", "SELL", "STRONG_SELL", "HOLD"};
        
        for (String recommendation : recommendations) {
            Reasoning reasoning = new Reasoning(
                "observation", "analysis", "risk", recommendation, 50, Instant.now()
            );
            assertEquals(recommendation, reasoning.getRecommendation());
        }
    }
}
