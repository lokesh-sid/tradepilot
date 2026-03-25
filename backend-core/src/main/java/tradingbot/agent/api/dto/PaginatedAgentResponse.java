package tradingbot.agent.api.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Paginated list of AI trading agents")
public record PaginatedAgentResponse(
    @Schema(description = "Agent items for the current page")
    List<AgentResponse> content,

    @Schema(description = "Zero-based page index", example = "0")
    int page,

    @Schema(description = "Page size", example = "20")
    int size,

    @Schema(description = "Total number of agents owned by the user", example = "42")
    long totalElements
) {}
