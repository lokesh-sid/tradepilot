package tradingbot.bot.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response for bot stop operation")
public class BotStopResponse extends BaseResponse {
    
    @Schema(description = "Success message", example = "Trading bot stopped successfully")
    @JsonProperty("message")
    private String message;
    
    @Schema(description = "Stop timestamp", example = "1696070400000")
    @JsonProperty("stoppedAt")
    private long stoppedAt;
    
    @Schema(description = "Final position status before stop", example = "CLOSED")
    @JsonProperty("finalPositionStatus")
    private String finalPositionStatus;
    
    @Schema(description = "Bot was running when stopped", example = "true")
    @JsonProperty("wasRunning")
    private boolean wasRunning;
    
    public BotStopResponse() {
        this.stoppedAt = System.currentTimeMillis();
    }
    
    public BotStopResponse(String message, String finalPositionStatus, boolean wasRunning) {
        this.message = message;
        this.finalPositionStatus = finalPositionStatus;
        this.wasRunning = wasRunning;
        this.stoppedAt = System.currentTimeMillis();
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public long getStoppedAt() {
        return stoppedAt;
    }
    
    public void setStoppedAt(long stoppedAt) {
        this.stoppedAt = stoppedAt;
    }
    
    public String getFinalPositionStatus() {
        return finalPositionStatus;
    }
    
    public void setFinalPositionStatus(String finalPositionStatus) {
        this.finalPositionStatus = finalPositionStatus;
    }
    
    public boolean isWasRunning() {
        return wasRunning;
    }
    
    public void setWasRunning(boolean wasRunning) {
        this.wasRunning = wasRunning;
    }
}