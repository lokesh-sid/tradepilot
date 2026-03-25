package tradingbot.bot.controller.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response for bot deletion API
 */
@Schema(description = "Response returned after deleting a trading bot")
public class BotDeletedResponse extends BaseResponse {
    
    @Schema(description = "Deletion confirmation message", example = "Bot 550e8400-e29b-41d4-a716-446655440000 deleted successfully")
    private String message;

    public BotDeletedResponse() {
        super();
    }

    public BotDeletedResponse(String message) {
        super();
        this.message = message;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
