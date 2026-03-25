package tradingbot.bot.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event data for bot status events.
 */
public class BotStatusData {
    
    @JsonProperty("botId")
    private String botId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("previousStatus")
    private String previousStatus;
    
    @JsonProperty("reason")
    private String reason;
    
    @JsonProperty("isRunning")
    private Boolean isRunning;
    
    @JsonProperty("mode")
    private String mode;
    
    public String getBotId() {
        return botId;
    }
    
    public void setBotId(String botId) {
        this.botId = botId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getPreviousStatus() {
        return previousStatus;
    }
    
    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public Boolean getIsRunning() {
        return isRunning;
    }
    
    public void setIsRunning(Boolean isRunning) {
        this.isRunning = isRunning;
    }
    
    public String getMode() {
        return mode;
    }
    
    public void setMode(String mode) {
        this.mode = mode;
    }
    
    @Override
    public String toString() {
        return "BotStatusData{" +
                "botId='" + botId + '\'' +
                ", status='" + status + '\'' +
                ", previousStatus='" + previousStatus + '\'' +
                ", reason='" + reason + '\'' +
                ", isRunning=" + isRunning +
                ", mode='" + mode + '\'' +
                '}';
    }
}
