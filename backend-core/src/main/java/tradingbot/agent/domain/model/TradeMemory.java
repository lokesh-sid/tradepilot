package tradingbot.agent.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * TradeMemory - A record of a past trade with its context and outcome
 *
 * This domain object represents a single trading memory that can be:
 * 1. Stored in a vector database for semantic search
 * 2. Retrieved when making similar future decisions
 * 3. Used to augment LLM prompts with historical context
 *
 * The memory includes both structured data (prices, outcome) and
 * unstructured text (scenario description, lessons learned).
 */
public class TradeMemory {

    private final String id;
    private final String agentId;
    private final String symbol;
    private final String scenarioDescription;
    private final TradeDirection direction;
    private final double entryPrice;
    private final Double exitPrice;  // Nullable - may still be open
    private final TradeOutcome outcome;
    private final Double profitPercent;  // Nullable - may still be open
    private final String lessonLearned;
    private final Double networkFee;
    private final Instant timestamp;
    private final double[] embedding;  // Vector representation for semantic search

    // Transient field - calculated during retrieval, not stored
    private Double similarityScore;

    private TradeMemory(Builder builder) {
        this.id = builder.id;
        this.agentId = builder.agentId;
        this.symbol = builder.symbol;
        this.scenarioDescription = builder.scenarioDescription;
        this.direction = builder.direction;
        this.entryPrice = builder.entryPrice;
        this.exitPrice = builder.exitPrice;
        this.outcome = builder.outcome;
        this.profitPercent = builder.profitPercent;
        this.lessonLearned = builder.lessonLearned;
        this.networkFee = builder.networkFee;
        this.timestamp = builder.timestamp;
        this.embedding = builder.embedding;
        this.similarityScore = builder.similarityScore;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getId() { return id; }
    public String getAgentId() { return agentId; }
    public String getSymbol() { return symbol; }
    public String getScenarioDescription() { return scenarioDescription; }
    public TradeDirection getDirection() { return direction; }
    public double getEntryPrice() { return entryPrice; }
    public Double getExitPrice() { return exitPrice; }
    public TradeOutcome getOutcome() { return outcome; }
    public Double getProfitPercent() { return profitPercent; }
    public String getLessonLearned() { return lessonLearned; }
    public Double getNetworkFee() { return networkFee; }
    public Instant getTimestamp() { return timestamp; }
    public double[] getEmbedding() { return embedding; }
    public Double getSimilarityScore() { return similarityScore; }

    public void setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
    }

    /**
     * Check if this trade is still open (no exit yet)
     */
    public boolean isOpen() {
        return outcome == TradeOutcome.PENDING;
    }

    /**
     * Check if this trade was profitable
     */
    public boolean wasProfitable() {
        return outcome == TradeOutcome.PROFIT && profitPercent != null && profitPercent > 0;
    }

    /**
     * Format experience for display in LLM prompts
     */
    public String formatForPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("Trade #").append(id).append(" (").append(timestamp).append("):\n");
        sb.append("- Scenario: ").append(scenarioDescription).append("\n");
        sb.append("- Decision: ").append(direction).append(" ").append(symbol);
        sb.append(" at $").append(String.format("%.2f", entryPrice)).append("\n");

        if (!isOpen()) {
            sb.append("- Outcome: ").append(outcome);
            if (profitPercent != null) {
                sb.append(" (").append(String.format("%.2f", profitPercent)).append("% profit/loss)\n");
            } else {
                sb.append("\n");
            }
        } else {
            sb.append("- Outcome: STILL OPEN\n");
        }

        if (lessonLearned != null && !lessonLearned.isEmpty()) {
            sb.append("- Lesson Learned: ").append(lessonLearned).append("\n");
        }

        if (similarityScore != null) {
            sb.append("- Similarity Score: ").append(String.format("%.2f%%", similarityScore * 100)).append("\n");
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeMemory that = (TradeMemory) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TradeMemory{" +
                "id='" + id + '\'' +
                ", symbol='" + symbol + '\'' +
                ", direction=" + direction +
                ", outcome=" + outcome +
                ", profitPercent=" + profitPercent +
                '}';
    }

    /**
     * Builder for TradeMemory
     */
    public static class Builder {
        private String id;
        private String agentId;
        private String symbol;
        private String scenarioDescription;
        private TradeDirection direction;
        private double entryPrice;
        private Double exitPrice;
        private TradeOutcome outcome = TradeOutcome.PENDING;
        private Double profitPercent;
        private String lessonLearned;
        private Double networkFee;
        private Instant timestamp = Instant.now();
        private double[] embedding;
        private Double similarityScore;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder scenarioDescription(String scenarioDescription) {
            this.scenarioDescription = scenarioDescription;
            return this;
        }

        public Builder direction(TradeDirection direction) {
            this.direction = direction;
            return this;
        }

        public Builder entryPrice(double entryPrice) {
            this.entryPrice = entryPrice;
            return this;
        }

        public Builder exitPrice(Double exitPrice) {
            this.exitPrice = exitPrice;
            return this;
        }

        public Builder outcome(TradeOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder profitPercent(Double profitPercent) {
            this.profitPercent = profitPercent;
            return this;
        }

        public Builder lessonLearned(String lessonLearned) {
            this.lessonLearned = lessonLearned;
            return this;
        }

        public Builder networkFee(Double networkFee) {
            this.networkFee = networkFee;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder embedding(double[] embedding) {
            this.embedding = embedding;
            return this;
        }

        public Builder similarityScore(Double similarityScore) {
            this.similarityScore = similarityScore;
            return this;
        }

        public TradeMemory build() {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(agentId, "agentId must not be null");
            Objects.requireNonNull(symbol, "symbol must not be null");
            Objects.requireNonNull(scenarioDescription, "scenarioDescription must not be null");
            Objects.requireNonNull(direction, "direction must not be null");
            Objects.requireNonNull(timestamp, "timestamp must not be null");

            return new TradeMemory(this);
        }
    }
}