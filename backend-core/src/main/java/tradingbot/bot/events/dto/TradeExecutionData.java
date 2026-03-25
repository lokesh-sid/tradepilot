package tradingbot.bot.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event data for trade execution events.
 */
public class TradeExecutionData {
    
    @JsonProperty("orderId")
    private String orderId;
    
    @JsonProperty("agentId")
    private String agentId;
    
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("side")
    private String side;
    
    @JsonProperty("executedQuantity")
    private Double executedQuantity;
    
    @JsonProperty("executedPrice")
    private Double executedPrice;
    
    @JsonProperty("commission")
    private Double commission;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("executionTime")
    private Long executionTime;
    
    public String getOrderId() {
        return orderId;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    public String getAgentId() {
        return agentId;
    }
    
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public String getSide() {
        return side;
    }
    
    public void setSide(String side) {
        this.side = side;
    }
    
    public Double getExecutedQuantity() {
        return executedQuantity;
    }
    
    public void setExecutedQuantity(Double executedQuantity) {
        this.executedQuantity = executedQuantity;
    }
    
    public Double getExecutedPrice() {
        return executedPrice;
    }
    
    public void setExecutedPrice(Double executedPrice) {
        this.executedPrice = executedPrice;
    }
    
    public Double getCommission() {
        return commission;
    }
    
    public void setCommission(Double commission) {
        this.commission = commission;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Long getExecutionTime() {
        return executionTime;
    }
    
    public void setExecutionTime(Long executionTime) {
        this.executionTime = executionTime;
    }
    
    @Override
    public String toString() {
        return "TradeExecutionData{" +
                "orderId='" + orderId + '\'' +
                ", agentId='" + agentId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side='" + side + '\'' +
                ", executedQuantity=" + executedQuantity +
                ", executedPrice=" + executedPrice +
                ", commission=" + commission +
                ", status='" + status + '\'' +
                ", executionTime=" + executionTime +
                '}';
    }
}
