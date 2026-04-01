package tradingbot.agent.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * TradeJournalEntity — canonical record for every trade decision.
 *
 * <p>Written in two phases:
 * <ul>
 *   <li><b>Entry</b> — {@link tradingbot.agent.application.TradeExecutionService} populates
 *       identity, decision context, and market snapshot at the moment the position opens.</li>
 *   <li><b>Close</b> — {@link tradingbot.agent.application.TradeReflectionListener} fills in
 *       exit price, realized PnL, outcome, and the LLM-generated lesson after the trade closes.</li>
 * </ul>
 *
 * <p>Human annotations ({@code notes}, {@code tags}, {@code conviction}, {@code reviewNotes})
 * are written via {@code PATCH /api/journal/{id}} at any time.
 *
 * <p>{@code llmBatchAnalysis} and {@code flaggedForReview} are written by
 * {@link tradingbot.agent.application.LLMStrategyReviewService} during the weekly batch review.
 */
@Entity
@Table(name = "trade_journal", indexes = {
    @Index(name = "idx_journal_agent_id",   columnList = "agent_id"),
    @Index(name = "idx_journal_symbol",     columnList = "symbol"),
    @Index(name = "idx_journal_outcome",    columnList = "outcome"),
    @Index(name = "idx_journal_decided_at", columnList = "decided_at")
})
public class TradeJournalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(name = "entry_order_id")
    private Long entryOrderId;

    // -------------------------------------------------------------------------
    // Decision context (set at entry, ephemeral)
    // -------------------------------------------------------------------------

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Direction direction;

    @Column(name = "entry_price", nullable = false)
    private double entryPrice;

    @Column(nullable = false)
    private double quantity;

    @Column(name = "stop_loss")
    private Double stopLoss;

    @Column(name = "take_profit")
    private Double takeProfit;

    @Column
    private Integer confidence;

    @Column(name = "llm_reasoning", columnDefinition = "TEXT")
    private String llmReasoning;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    // -------------------------------------------------------------------------
    // Outcome (set at close)
    // -------------------------------------------------------------------------

    @Column(name = "exit_price")
    private Double exitPrice;

    @Column(name = "realized_pnl")
    private Double realizedPnl;

    @Column(name = "pnl_percent")
    private Double pnlPercent;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Outcome outcome = Outcome.PENDING;

    @Column(name = "close_reason", length = 30)
    private String closeReason;

    @Column(name = "llm_lesson", columnDefinition = "TEXT")
    private String llmLesson;

    @Column(name = "closed_at")
    private Instant closedAt;

    // -------------------------------------------------------------------------
    // Human journal fields (set via PATCH)
    // -------------------------------------------------------------------------

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Comma-separated tag strings, e.g. {@code "fomo,late-entry,trending-market"}. */
    @Column(length = 1000)
    private String tags;

    @Column
    private Short conviction;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    // -------------------------------------------------------------------------
    // Feedback loop fields (set by LLMStrategyReviewService)
    // -------------------------------------------------------------------------

    @Column(name = "llm_batch_analysis", columnDefinition = "TEXT")
    private String llmBatchAnalysis;

    @Column(name = "flagged_for_review", nullable = false)
    private boolean flaggedForReview = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public TradeJournalEntity() {}

    // -------------------------------------------------------------------------
    // Enums
    // -------------------------------------------------------------------------

    public enum Direction {
        LONG, SHORT
    }

    public enum Outcome {
        PENDING, PROFIT, LOSS, BREAKEVEN, CANCELLED
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public Long getEntryOrderId() { return entryOrderId; }
    public void setEntryOrderId(Long entryOrderId) { this.entryOrderId = entryOrderId; }

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }

    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public Double getStopLoss() { return stopLoss; }
    public void setStopLoss(Double stopLoss) { this.stopLoss = stopLoss; }

    public Double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(Double takeProfit) { this.takeProfit = takeProfit; }

    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }

    public String getLlmReasoning() { return llmReasoning; }
    public void setLlmReasoning(String llmReasoning) { this.llmReasoning = llmReasoning; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }

    public Double getExitPrice() { return exitPrice; }
    public void setExitPrice(Double exitPrice) { this.exitPrice = exitPrice; }

    public Double getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(Double realizedPnl) { this.realizedPnl = realizedPnl; }

    public Double getPnlPercent() { return pnlPercent; }
    public void setPnlPercent(Double pnlPercent) { this.pnlPercent = pnlPercent; }

    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }

    public String getCloseReason() { return closeReason; }
    public void setCloseReason(String closeReason) { this.closeReason = closeReason; }

    public String getLlmLesson() { return llmLesson; }
    public void setLlmLesson(String llmLesson) { this.llmLesson = llmLesson; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public Short getConviction() { return conviction; }
    public void setConviction(Short conviction) { this.conviction = conviction; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    public String getLlmBatchAnalysis() { return llmBatchAnalysis; }
    public void setLlmBatchAnalysis(String llmBatchAnalysis) { this.llmBatchAnalysis = llmBatchAnalysis; }

    public boolean isFlaggedForReview() { return flaggedForReview; }
    public void setFlaggedForReview(boolean flaggedForReview) { this.flaggedForReview = flaggedForReview; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
