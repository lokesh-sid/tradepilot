package tradingbot.bot.controller.dto.response;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import tradingbot.bot.TradeDirection;

@Schema(description = "Trade execution response")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradeExecutionResponse {
    
    @Schema(description = "Trade identifier", example = "trade-456")
    @JsonProperty("tradeId")
    private String tradeId;
    
    @Schema(description = "Bot identifier", example = "bot-123")
    @JsonProperty("botId")
    private String botId;
    
    @Schema(description = "Order status", example = "FILLED")
    @JsonProperty("status")
    private OrderStatus status;
    
    @Schema(description = "Trading symbol", example = "BTCUSDT")
    @JsonProperty("symbol")
    private String symbol;
    
    @Schema(description = "Trade direction", example = "LONG")
    @JsonProperty("direction")
    private TradeDirection direction;
    
    @Schema(description = "Requested quantity", example = "0.001")
    @JsonProperty("requestedQuantity")
    private Double requestedQuantity;
    
    @Schema(description = "Executed quantity", example = "0.001")
    @JsonProperty("executedQuantity")
    private Double executedQuantity;
    
    @Schema(description = "Average execution price", example = "50050.0")
    @JsonProperty("avgExecutionPrice")
    private Double avgExecutionPrice;
    
    @Schema(description = "Total cost (including fees)", example = "50.075")
    @JsonProperty("totalCost")
    private Double totalCost;
    
    @Schema(description = "Trading fees", example = "0.025")
    @JsonProperty("fees")
    private Double fees;
    
    @Schema(description = "Exchange order ID", example = "12345678")
    @JsonProperty("exchangeOrderId")
    private String exchangeOrderId;
    
    @Schema(description = "Execution timestamp", example = "2024-11-07T12:00:00Z")
    @JsonProperty("executionTime")
    private Instant executionTime;
    
    @Schema(description = "Stop loss price", example = "49000.0")
    @JsonProperty("stopLossPrice")
    private Double stopLossPrice;
    
    @Schema(description = "Take profit price", example = "52500.0")
    @JsonProperty("takeProfitPrice")
    private Double takeProfitPrice;
    
    @Schema(description = "Warnings or notes")
    @JsonProperty("warnings")
    private List<String> warnings;
    
    public TradeExecutionResponse() {
        this.executionTime = Instant.now();
        this.warnings = new ArrayList<>();
    }
    
    public void addWarning(String warning) {
        if (this.warnings == null) {
            this.warnings = new ArrayList<>();
        }
        this.warnings.add(warning);
    }
    
    // Getters and Setters
    public String getTradeId() {
        return tradeId;
    }
    
    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }
    
    public String getBotId() {
        return botId;
    }
    
    public void setBotId(String botId) {
        this.botId = botId;
    }
    
    public OrderStatus getStatus() {
        return status;
    }
    
    public void setStatus(OrderStatus status) {
        this.status = status;
    }
    
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
    
    public Double getRequestedQuantity() {
        return requestedQuantity;
    }
    
    public void setRequestedQuantity(Double requestedQuantity) {
        this.requestedQuantity = requestedQuantity;
    }
    
    public Double getExecutedQuantity() {
        return executedQuantity;
    }
    
    public void setExecutedQuantity(Double executedQuantity) {
        this.executedQuantity = executedQuantity;
    }
    
    public Double getAvgExecutionPrice() {
        return avgExecutionPrice;
    }
    
    public void setAvgExecutionPrice(Double avgExecutionPrice) {
        this.avgExecutionPrice = avgExecutionPrice;
    }
    
    public Double getTotalCost() {
        return totalCost;
    }
    
    public void setTotalCost(Double totalCost) {
        this.totalCost = totalCost;
    }
    
    public Double getFees() {
        return fees;
    }
    
    public void setFees(Double fees) {
        this.fees = fees;
    }
    
    public String getExchangeOrderId() {
        return exchangeOrderId;
    }
    
    public void setExchangeOrderId(String exchangeOrderId) {
        this.exchangeOrderId = exchangeOrderId;
    }
    
    public Instant getExecutionTime() {
        return executionTime;
    }
    
    public void setExecutionTime(Instant executionTime) {
        this.executionTime = executionTime;
    }
    
    public Double getStopLossPrice() {
        return stopLossPrice;
    }
    
    public void setStopLossPrice(Double stopLossPrice) {
        this.stopLossPrice = stopLossPrice;
    }
    
    public Double getTakeProfitPrice() {
        return takeProfitPrice;
    }
    
    public void setTakeProfitPrice(Double takeProfitPrice) {
        this.takeProfitPrice = takeProfitPrice;
    }
    
    public List<String> getWarnings() {
        return warnings;
    }
    
    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
    
    public enum OrderStatus {
        PENDING,
        FILLED,
        PARTIALLY_FILLED,
        CANCELLED,
        REJECTED
    }
}
