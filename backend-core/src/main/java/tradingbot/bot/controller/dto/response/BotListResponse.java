package tradingbot.bot.controller.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response for bot list API with pagination support
 */
@Schema(description = "Paginated list of trading bots")
public class BotListResponse extends BaseResponse {
    
    @Schema(description = "List of bot IDs in the current page")
    private List<String> botIds;
    
    @Schema(description = "Pagination information")
    private PaginationInfo pagination;
    
    @Schema(description = "Number of bots currently active in memory", example = "5")
    private int activeInMemory;

    public BotListResponse() {
        super();
    }

    public BotListResponse(List<String> botIds, PaginationInfo pagination, int activeInMemory) {
        super();
        this.botIds = botIds;
        this.pagination = pagination;
        this.activeInMemory = activeInMemory;
    }

    // Getters and setters
    public List<String> getBotIds() { return botIds; }
    public void setBotIds(List<String> botIds) { this.botIds = botIds; }

    public PaginationInfo getPagination() { return pagination; }
    public void setPagination(PaginationInfo pagination) { this.pagination = pagination; }

    public int getActiveInMemory() { return activeInMemory; }
    public void setActiveInMemory(int activeInMemory) { this.activeInMemory = activeInMemory; }
}
