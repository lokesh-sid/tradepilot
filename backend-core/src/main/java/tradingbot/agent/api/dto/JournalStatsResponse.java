package tradingbot.agent.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aggregate statistics for an agent's trade journal")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JournalStatsResponse(

    @Schema(description = "Total closed trades")
    long totalTrades,

    @Schema(description = "Win rate 0.0–1.0", example = "0.54")
    double winRate,

    @Schema(description = "Average PnL percent across all closed trades")
    Double avgPnlPercent,

    @Schema(description = "Average LLM confidence score on winning trades")
    Double avgConfidenceOnWins,

    @Schema(description = "Average LLM confidence score on losing trades")
    Double avgConfidenceOnLosses,

    @Schema(description = "Win rate and count broken down by tag")
    List<TagStatDTO> topTags
) {

    @Schema(description = "Win rate statistics for a single tag")
    public record TagStatDTO(
        @Schema(description = "Tag name", example = "fomo")
        String tag,

        @Schema(description = "Number of trades with this tag")
        long count,

        @Schema(description = "Win rate for trades with this tag", example = "0.31")
        double winRate
    ) {}
}
