package tradingbot.bot.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import tradingbot.bot.controller.validation.ValidLeverage;

@Schema(description = "Request to update leverage")
public class LeverageUpdateRequest {
    
    @NotNull(message = "Leverage is required")
    @ValidLeverage(min = 1, max = 125, message = "Leverage must be between 1 and 125")
    @Schema(description = "Leverage multiplier (1-125)", example = "10", minimum = "1", maximum = "125")
    @JsonProperty("leverage")
    private Double leverage;
    
    public LeverageUpdateRequest() {}
    
    public LeverageUpdateRequest(Double leverage) {
        this.leverage = leverage;
    }
    
    public Double getLeverage() {
        return leverage;
    }
    
    public void setLeverage(Double leverage) {
        this.leverage = leverage;
    }
}