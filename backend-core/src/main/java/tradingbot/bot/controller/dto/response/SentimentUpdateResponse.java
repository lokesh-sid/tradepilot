package tradingbot.bot.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response for sentiment analysis toggle operation")
public class SentimentUpdateResponse extends BaseResponse {
    
    @Schema(description = "Success message", example = "Sentiment analysis enabled")
    @JsonProperty("message")
    private String message;
    
    @Schema(description = "Current sentiment analysis status", example = "true")
    @JsonProperty("sentimentEnabled")
    private boolean sentimentEnabled;
    
    @Schema(description = "Previous sentiment analysis status", example = "false")
    @JsonProperty("previousStatus")
    private boolean previousStatus;
    
    public SentimentUpdateResponse() {
        super();
    }
    
    public SentimentUpdateResponse(String message, boolean sentimentEnabled, boolean previousStatus) {
        super();
        this.message = message;
        this.sentimentEnabled = sentimentEnabled;
        this.previousStatus = previousStatus;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public boolean isSentimentEnabled() {
        return sentimentEnabled;
    }
    
    public void setSentimentEnabled(boolean sentimentEnabled) {
        this.sentimentEnabled = sentimentEnabled;
    }
    
    public boolean isPreviousStatus() {
        return previousStatus;
    }
    
    public void setPreviousStatus(boolean previousStatus) {
        this.previousStatus = previousStatus;
    }
}