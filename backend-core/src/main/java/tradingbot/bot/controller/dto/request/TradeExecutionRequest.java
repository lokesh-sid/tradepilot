package tradingbot.bot.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import tradingbot.bot.TradeDirection;

@Schema(description = "Request to execute a trade")
public class TradeExecutionRequest {
    
    @NotBlank(message = "Symbol is required")
    @Pattern(regexp = "^[A-Z0-9]{4,20}$", message = "Symbol must be 4-20 uppercase alphanumeric characters")
    @Schema(description = "Trading symbol", example = "BTCUSDT", required = true)
    @JsonProperty("symbol")
    private String symbol;
    
    @NotNull(message = "Trade direction is required")
    @Schema(description = "Trade direction (LONG or SHORT)", example = "LONG", required = true)
    @JsonProperty("direction")
    private TradeDirection direction;
    
    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    @Schema(description = "Trade quantity", example = "0.001", required = true, minimum = "0.000001")
    @JsonProperty("quantity")
    private Double quantity;
    
    @Schema(description = "Order type", example = "MARKET", defaultValue = "MARKET")
    @JsonProperty("orderType")
    private OrderType orderType = OrderType.MARKET;
    
    @Positive(message = "Limit price must be positive")
    @Schema(description = "Limit price (required for LIMIT orders)", example = "50000.0")
    @JsonProperty("limitPrice")
    private Double limitPrice;
    
    @DecimalMin(value = "0.1", message = "Stop loss must be at least 0.1%")
    @DecimalMax(value = "50.0", message = "Stop loss cannot exceed 50%")
    @Schema(description = "Stop loss percentage", example = "2.0")
    @JsonProperty("stopLossPercent")
    private Double stopLossPercent;
    
    @DecimalMin(value = "0.1", message = "Take profit must be at least 0.1%")
    @DecimalMax(value = "100.0", message = "Take profit cannot exceed 100%")
    @Schema(description = "Take profit percentage", example = "5.0")
    @JsonProperty("takeProfitPercent")
    private Double takeProfitPercent;
    
    @Schema(description = "Time in force", example = "GTC", defaultValue = "GTC")
    @JsonProperty("timeInForce")
    private TimeInForce timeInForce = TimeInForce.GTC;
    
    @Schema(description = "Reduce-only order", example = "false", defaultValue = "false")
    @JsonProperty("reduceOnly")
    private Boolean reduceOnly = false;
    
    public TradeExecutionRequest() {}
    
    // Cross-field validation
    @AssertTrue(message = "Limit price is required for LIMIT orders")
    private boolean isLimitPriceValid() {
        return orderType != OrderType.LIMIT || limitPrice != null;
    }
    
    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public TradeDirection getDirection() {
        return direction;
    }
    
    public void setDirection(TradeDirection direction) {
        this.direction = direction;
    }
    
    public Double getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }
    
    public OrderType getOrderType() {
        return orderType;
    }
    
    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }
    
    public Double getLimitPrice() {
        return limitPrice;
    }
    
    public void setLimitPrice(Double limitPrice) {
        this.limitPrice = limitPrice;
    }
    
    public Double getStopLossPercent() {
        return stopLossPercent;
    }
    
    public void setStopLossPercent(Double stopLossPercent) {
        this.stopLossPercent = stopLossPercent;
    }
    
    public Double getTakeProfitPercent() {
        return takeProfitPercent;
    }
    
    public void setTakeProfitPercent(Double takeProfitPercent) {
        this.takeProfitPercent = takeProfitPercent;
    }
    
    public TimeInForce getTimeInForce() {
        return timeInForce;
    }
    
    public void setTimeInForce(TimeInForce timeInForce) {
        this.timeInForce = timeInForce;
    }
    
    public Boolean getReduceOnly() {
        return reduceOnly;
    }
    
    public void setReduceOnly(Boolean reduceOnly) {
        this.reduceOnly = reduceOnly;
    }
    
    public enum OrderType {
        MARKET,
        LIMIT
    }
    
    public enum TimeInForce {
        GTC,  // Good Till Cancel
        IOC,  // Immediate or Cancel
        FOK   // Fill or Kill
    }
}
