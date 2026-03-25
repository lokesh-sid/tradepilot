package tradingbot.agent.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * TradeMemoryEntity - JPA entity for TradeMemory persistence
 */
@Entity
@Table(name = "trading_experiences", indexes = {
    @Index(name = "idx_experiences_agent_id", columnList = "agent_id"),
    @Index(name = "idx_experiences_symbol", columnList = "symbol"),
    @Index(name = "idx_experiences_outcome", columnList = "outcome"),
    @Index(name = "idx_experiences_timestamp", columnList = "timestamp")
})
public class TradeMemoryEntity {

    @Id
    private String id;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "scenario_description", nullable = false, length = 2000)
    private String scenarioDescription;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Direction direction;

    @Column(name = "entry_price", nullable = false)
    private double entryPrice;

    @Column(name = "exit_price")
    private Double exitPrice;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    @Column(name = "profit_percent")
    private Double profitPercent;

    @Column(name = "lesson_learned", length = 2000)
    private String lessonLearned;

    @Column(nullable = false)
    private Instant timestamp;

    // Note: embedding vector is stored in vector DB (Pinecone), not in PostgreSQL
    // PostgreSQL is used for metadata and structured queries

    // Constructors
    public TradeMemoryEntity() {}

    public TradeMemoryEntity(String id, String agentId, String symbol,
                              String scenarioDescription, Direction direction,
                              double entryPrice, Double exitPrice, Outcome outcome,
                              Double profitPercent, String lessonLearned, Instant timestamp) {
        this.id = id;
        this.agentId = agentId;
        this.symbol = symbol;
        this.scenarioDescription = scenarioDescription;
        this.direction = direction;
        this.entryPrice = entryPrice;
        this.exitPrice = exitPrice;
        this.outcome = outcome;
        this.profitPercent = profitPercent;
        this.lessonLearned = lessonLearned;
        this.timestamp = timestamp;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getScenarioDescription() { return scenarioDescription; }
    public void setScenarioDescription(String scenarioDescription) {
        this.scenarioDescription = scenarioDescription;
    }

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }

    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }

    public Double getExitPrice() { return exitPrice; }
    public void setExitPrice(Double exitPrice) { this.exitPrice = exitPrice; }

    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }

    public Double getProfitPercent() { return profitPercent; }
    public void setProfitPercent(Double profitPercent) { this.profitPercent = profitPercent; }

    public String getLessonLearned() { return lessonLearned; }
    public void setLessonLearned(String lessonLearned) { this.lessonLearned = lessonLearned; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    // Enums
    public enum Direction {
        LONG, SHORT
    }

    public enum Outcome {
        PROFIT, LOSS, BREAKEVEN, PENDING, CANCELLED
    }
}