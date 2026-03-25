package tradingbot.bot.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response for bot start operation")
public class BotStartResponse extends BaseResponse {
    
    @Schema(description = "Success message", example = "Trading bot started in LONG mode (live)")
    @JsonProperty("message")
    private String message;
    
    @Schema(description = "Bot status information")
    @JsonProperty("botStatus")
    private BotStatusResponse botStatus;
    
    @Schema(description = "Trading mode", example = "live")
    @JsonProperty("mode")
    private String mode;
    
    @Schema(description = "Trade direction", example = "LONG")
    @JsonProperty("direction")
    private String direction;
    
    @Schema(description = "Operation timestamp", example = "1696070400000")
    @JsonProperty("timestamp")
    private long timestamp;
    
    public BotStartResponse() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public BotStartResponse(String message, BotStatusResponse botStatus, String mode, String direction) {
        this.message = message;
        this.botStatus = botStatus;
        this.mode = mode;
        this.direction = direction;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public BotStatusResponse getBotStatus() {
        return botStatus;
    }
    
    public void setBotStatus(BotStatusResponse botStatus) {
        this.botStatus = botStatus;
    }
    
    public String getMode() {
        return mode;
    }
    
    public void setMode(String mode) {
        this.mode = mode;
    }
    
    public String getDirection() {
        return direction;
    }
    
    public void setDirection(String direction) {
        this.direction = direction;
    }
    
    @Override
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}