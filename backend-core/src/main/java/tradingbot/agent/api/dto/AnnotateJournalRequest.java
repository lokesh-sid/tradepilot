package tradingbot.agent.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Human annotation fields for a journal entry — all fields are optional; omit to leave unchanged")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnnotateJournalRequest(

    @Schema(description = "Free-text notes about this trade")
    String notes,

    @Schema(description = "Tags to apply, e.g. [\"fomo\", \"late-entry\"]")
    List<String> tags,

    @Schema(description = "Pre-trade conviction level 1 (low) to 5 (high)")
    @Min(1) @Max(5)
    Short conviction,

    @Schema(description = "Post-review reflection notes")
    String reviewNotes,

    @Schema(description = "Set true to stamp reviewedAt with the current time")
    boolean markReviewed
) {}
