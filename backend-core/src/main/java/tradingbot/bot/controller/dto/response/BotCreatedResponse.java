package tradingbot.bot.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response for bot creation API
 */
@Schema(description = "Response returned after creating a new trading bot")
public class BotCreatedResponse extends BaseResponse {
    
    @Schema(description = "Unique identifier of the created bot", example = "550e8400-e29b-41d4-a716-446655440000")
    private String botId;
    
    @Schema(description = "Success message", example = "Trading bot created successfully")
    private String message;

    public BotCreatedResponse() {
        super();
    }

    public BotCreatedResponse(String botId, String message) {
        super();
        this.botId = botId;
        this.message = message;
    }

    public String getBotId() { return botId; }
    public void setBotId(String botId) { this.botId = botId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
