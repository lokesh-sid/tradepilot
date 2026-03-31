package tradingbot.agent.api.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A single trade journal entry")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JournalEntryResponse(

    @Schema(description = "Journal entry ID")
    String id,

    @Schema(description = "Agent ID that made the decision")
    String agentId,

    @Schema(description = "Trading pair", example = "BTCUSDT")
    String symbol,

    @Schema(description = "Entry order ID")
    String entryOrderId,

    // --- Decision context ---

    @Schema(description = "Trade direction", example = "LONG")
    String direction,

    @Schema(description = "Entry fill price")
    double entryPrice,

    @Schema(description = "Fill quantity")
    double quantity,

    @Schema(description = "Stop-loss price")
    Double stopLoss,

    @Schema(description = "Take-profit price")
    Double takeProfit,

    @Schema(description = "LLM confidence score 0–100")
    Integer confidence,

    @Schema(description = "Full LLM reasoning at decision time")
    String llmReasoning,

    @Schema(description = "When the decision was produced")
    Instant decidedAt,

    // --- Outcome ---

    @Schema(description = "Exit fill price")
    Double exitPrice,

    @Schema(description = "Realized PnL in USD")
    Double realizedPnl,

    @Schema(description = "Realized PnL as percentage")
    Double pnlPercent,

    @Schema(description = "Trade outcome", example = "PROFIT")
    String outcome,

    @Schema(description = "How the trade closed", example = "TAKE_PROFIT")
    String closeReason,

    @Schema(description = "LLM-generated lesson learned")
    String llmLesson,

    @Schema(description = "When the position closed")
    Instant closedAt,

    // --- Human journal fields ---

    @Schema(description = "Human notes")
    String notes,

    @Schema(description = "Tags", example = "[\"fomo\", \"trending-market\"]")
    List<String> tags,

    @Schema(description = "Pre-trade conviction 1–5")
    Short conviction,

    @Schema(description = "When this entry was reviewed")
    Instant reviewedAt,

    @Schema(description = "Post-review reflection notes")
    String reviewNotes,

    // --- Feedback loop ---

    @Schema(description = "Insight from LLM batch strategy review")
    String llmBatchAnalysis,

    @Schema(description = "Flagged by LLM batch review for human attention")
    boolean flaggedForReview,

    @Schema(description = "When this journal entry was created")
    Instant createdAt
) {}
