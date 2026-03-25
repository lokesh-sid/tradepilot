package tradingbot.bot.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to update sentiment analysis setting")
public class SentimentUpdateRequest {
    
    @NotNull(message = "Enable flag is required")
    @Schema(description = "Enable or disable sentiment analysis", example = "true")
    @JsonProperty("enable")
    private Boolean enable;
    
    public SentimentUpdateRequest() {}
    
    public SentimentUpdateRequest(Boolean enable) {
        this.enable = enable;
    }
    
    public Boolean getEnable() {
        return enable;
    }
    
    public void setEnable(Boolean enable) {
        this.enable = enable;
    }
}