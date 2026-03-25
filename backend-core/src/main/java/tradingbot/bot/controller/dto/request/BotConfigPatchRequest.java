package tradingbot.bot.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@Schema(description = "Partial bot configuration update")
public class BotConfigPatchRequest {
    
    @Pattern(regexp = "^[A-Z0-9]{4,20}$", message = "Symbol must be 4-20 uppercase alphanumeric characters")
    @Schema(description = "Trading symbol", example = "BTCUSDT")
    @JsonProperty("symbol")
    private String symbol;
    
    @Min(value = 1, message = "Leverage must be at least 1")
    @Max(value = 125, message = "Leverage must not exceed 125")
    @Schema(description = "Leverage multiplier (1-125)", example = "10", minimum = "1", maximum = "125")
    @JsonProperty("leverage")
    private Integer leverage;
    
    @Positive(message = "Position size must be positive")
    @Schema(description = "Position size in USDT", example = "100.0", minimum = "0.01")
    @JsonProperty("positionSize")
    private Double positionSize;
    
    @DecimalMin(value = "0.1", message = "Stop loss must be at least 0.1%")
    @DecimalMax(value = "50.0", message = "Stop loss cannot exceed 50%")
    @Schema(description = "Stop loss percentage (0.1-50)", example = "2.0", minimum = "0.1", maximum = "50")
    @JsonProperty("stopLoss")
    private Double stopLoss;
    
    @DecimalMin(value = "0.1", message = "Take profit must be at least 0.1%")
    @DecimalMax(value = "100.0", message = "Take profit cannot exceed 100%")
    @Schema(description = "Take profit percentage (0.1-100)", example = "5.0", minimum = "0.1", maximum = "100")
    @JsonProperty("takeProfit")
    private Double takeProfit;
    
    @Schema(description = "Enable sentiment analysis", example = "true")
    @JsonProperty("sentimentEnabled")
    private Boolean sentimentEnabled;
    
    public BotConfigPatchRequest() {}
    
    // Validation: At least one field must be present
    @AssertTrue(message = "At least one field must be provided for update")
    private boolean hasAtLeastOneField() {
        return symbol != null || leverage != null || positionSize != null 
            || stopLoss != null || takeProfit != null || sentimentEnabled != null;
    }
    
    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public Integer getLeverage() {
        return leverage;
    }
    
    public void setLeverage(Integer leverage) {
        this.leverage = leverage;
    }
    
    public Double getPositionSize() {
        return positionSize;
    }
    
    public void setPositionSize(Double positionSize) {
        this.positionSize = positionSize;
    }
    
    public Double getStopLoss() {
        return stopLoss;
    }
    
    public void setStopLoss(Double stopLoss) {
        this.stopLoss = stopLoss;
    }
    
    public Double getTakeProfit() {
        return takeProfit;
    }
    
    public void setTakeProfit(Double takeProfit) {
        this.takeProfit = takeProfit;
    }
    
    public Boolean getSentimentEnabled() {
        return sentimentEnabled;
    }
    
    public void setSentimentEnabled(Boolean sentimentEnabled) {
        this.sentimentEnabled = sentimentEnabled;
    }
}
