package tradingbot.agent.infrastructure.repository;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * AgentEntity - JPA Entity for Agent persistence
 * 
 * Maps Agent domain model to database table
 */
@Entity
@Table(name = "agents")
public class AgentEntity {
    
    @Id
    private String id;
    @Column(nullable = false, unique = true)
    private String name;
    @Column(name = "goal_type", nullable = false)
    private String goalType;
    @Column(name = "goal_description", columnDefinition = "TEXT")
    private String goalDescription;
    @Column(name = "trading_symbol", nullable = false)
    private String tradingSymbol;
    @Column(nullable = false)
    private double capital;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AgentStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_active_at")
    private Instant lastActiveAt;
    @Column(name = "iteration_count")
    private int iterationCount;
    // Perception fields
    @Column(name = "last_price")
    private Double lastPrice;
    @Column(name = "last_trend")
    private String lastTrend;
    @Column(name = "last_sentiment")
    private String lastSentiment;
    
    @Column(name = "last_volume")
    private Double lastVolume;
    @Column(name = "perceived_at")
    private Instant perceivedAt;
    // Reasoning fields
    @Column(name = "last_observation", columnDefinition = "TEXT")
    private String lastObservation;
    @Column(name = "last_analysis", columnDefinition = "TEXT")
    private String lastAnalysis;
    @Column(name = "last_risk_assessment", columnDefinition = "TEXT")
    private String lastRiskAssessment;
    @Column(name = "last_recommendation", columnDefinition = "TEXT")
    private String lastRecommendation;
    @Column(name = "last_confidence")
    private Integer lastConfidence;
    @Column(name = "reasoned_at")
    private Instant reasonedAt;
    @Column(name = "owner_id")
    private String ownerId;
    @Column(name = "execution_mode", nullable = false)
    @Enumerated(EnumType.STRING)
    private ExecutionMode executionMode;
    @Column(name = "exchange_name")
    private String exchangeName;

    // Builder pattern
    public static class Builder {
        private String id;
        private String name;
        private String goalType;
        private String goalDescription;
        private String tradingSymbol;
        private double capital;
        private AgentStatus status;
        private Instant createdAt;
        private Instant lastActiveAt;
        private int iterationCount;
        private Double lastPrice;
        private String lastTrend;
        private String lastSentiment;
        private Double lastVolume;
        private Instant perceivedAt;
        private String lastObservation;
        private String lastAnalysis;
        private String lastRiskAssessment;
        private String lastRecommendation;
        private Integer lastConfidence;
        private Instant reasonedAt;
        private String ownerId;
        private ExecutionMode executionMode = ExecutionMode.NONE;
        private String exchangeName;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder goalType(String goalType) { this.goalType = goalType; return this; }
        public Builder goalDescription(String goalDescription) { this.goalDescription = goalDescription; return this; }
        public Builder tradingSymbol(String tradingSymbol) { this.tradingSymbol = tradingSymbol; return this; }
        public Builder capital(double capital) { this.capital = capital; return this; }
        public Builder status(AgentStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder lastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; return this; }
        public Builder iterationCount(int iterationCount) { this.iterationCount = iterationCount; return this; }
        public Builder lastPrice(Double lastPrice) { this.lastPrice = lastPrice; return this; }
        public Builder lastTrend(String lastTrend) { this.lastTrend = lastTrend; return this; }
        public Builder lastSentiment(String lastSentiment) { this.lastSentiment = lastSentiment; return this; }
        public Builder lastVolume(Double lastVolume) { this.lastVolume = lastVolume; return this; }
        public Builder perceivedAt(Instant perceivedAt) { this.perceivedAt = perceivedAt; return this; }
        public Builder lastObservation(String lastObservation) { this.lastObservation = lastObservation; return this; }
        public Builder lastAnalysis(String lastAnalysis) { this.lastAnalysis = lastAnalysis; return this; }
        public Builder lastRiskAssessment(String lastRiskAssessment) { this.lastRiskAssessment = lastRiskAssessment; return this; }
        public Builder lastRecommendation(String lastRecommendation) { this.lastRecommendation = lastRecommendation; return this; }
        public Builder lastConfidence(Integer lastConfidence) { this.lastConfidence = lastConfidence; return this; }
        public Builder reasonedAt(Instant reasonedAt) { this.reasonedAt = reasonedAt; return this; }
        public Builder ownerId(String ownerId) { this.ownerId = ownerId; return this; }
        public Builder executionMode(ExecutionMode executionMode) { this.executionMode = executionMode; return this; }
        public Builder exchangeName(String exchangeName) { this.exchangeName = exchangeName; return this; }

        public AgentEntity build() {
            return new AgentEntity(this);
        }
    }

    protected AgentEntity() {
        // For JPA
    }

    private AgentEntity(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.goalType = builder.goalType;
        this.goalDescription = builder.goalDescription;
        this.tradingSymbol = builder.tradingSymbol;
        this.capital = builder.capital;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.lastActiveAt = builder.lastActiveAt;
        this.iterationCount = builder.iterationCount;
        this.lastPrice = builder.lastPrice;
        this.lastTrend = builder.lastTrend;
        this.lastSentiment = builder.lastSentiment;
        this.lastVolume = builder.lastVolume;
        this.perceivedAt = builder.perceivedAt;
        this.lastObservation = builder.lastObservation;
        this.lastAnalysis = builder.lastAnalysis;
        this.lastRiskAssessment = builder.lastRiskAssessment;
        this.lastRecommendation = builder.lastRecommendation;
        this.lastConfidence = builder.lastConfidence;
        this.reasonedAt = builder.reasonedAt;
        this.ownerId = builder.ownerId;
        this.executionMode = builder.executionMode != null ? builder.executionMode : ExecutionMode.NONE;
        this.exchangeName = builder.exchangeName;
    }

    // Getters only (no setters for builder pattern)
    public String getId() { return id; }
    public String getName() { return name; }
    public String getGoalType() { return goalType; }
    public String getGoalDescription() { return goalDescription; }
    public String getTradingSymbol() { return tradingSymbol; }
    public double getCapital() { return capital; }
    public AgentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public int getIterationCount() { return iterationCount; }
    public Double getLastPrice() { return lastPrice; }
    public String getLastTrend() { return lastTrend; }
    public String getLastSentiment() { return lastSentiment; }
    public Double getLastVolume() { return lastVolume; }
    public Instant getPerceivedAt() { return perceivedAt; }
    public String getLastObservation() { return lastObservation; }
    public String getLastAnalysis() { return lastAnalysis; }
    public String getLastRiskAssessment() { return lastRiskAssessment; }
    public String getLastRecommendation() { return lastRecommendation; }
    public Integer getLastConfidence() { return lastConfidence; }
    public Instant getReasonedAt() { return reasonedAt; }
    public String getOwnerId() { return ownerId; }
    public ExecutionMode getExecutionMode() { return executionMode; }
    public String getExchangeName() { return exchangeName; }

    /**
     * AgentStatus - Entity status enum
     */
    public enum AgentStatus {
        IDLE, ACTIVE, PAUSED, STOPPED
    }

    /**
     * ExecutionMode - How this agent/bot is being executed.
     * Separate from AgentGoal.GoalType which is a domain concept.
     */
    public enum ExecutionMode {
        FUTURES,
        FUTURES_PAPER,
        NONE
    }
}
