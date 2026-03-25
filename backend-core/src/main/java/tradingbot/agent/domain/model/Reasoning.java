package tradingbot.agent.domain.model;

import java.time.Instant;

/**
 * Reasoning - The agent's thought process and decision
 */
public class Reasoning {
    
    private final String observation;
    private final String analysis;
    private final String riskAssessment;
    private final String recommendation;
    private final int confidence;
    private final Instant timestamp;
    
    public Reasoning(String observation, String analysis, String riskAssessment,
                    String recommendation, int confidence, Instant timestamp) {
        this.observation = observation;
        this.analysis = analysis;
        this.riskAssessment = riskAssessment;
        this.recommendation = recommendation;
        this.confidence = Math.clamp(confidence, 0, 100); // 0-100
        this.timestamp = timestamp;
    }
    
    public String getObservation() { return observation; }
    public String getAnalysis() { return analysis; }
    public String getRiskAssessment() { return riskAssessment; }
    public String getRecommendation() { return recommendation; }
    public int getConfidence() { return confidence; }
    public Instant getTimestamp() { return timestamp; }
}
