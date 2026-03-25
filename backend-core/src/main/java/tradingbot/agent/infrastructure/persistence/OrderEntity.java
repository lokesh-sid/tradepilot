package tradingbot.agent.infrastructure.persistence;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tradingbot.agent.api.dto.OrderResponse;

/**
 * OrderEntity - JPA entity for Order persistence
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_agent_id", columnList = "agent_id"),
    @Index(name = "idx_orders_symbol", columnList = "symbol"),
    @Index(name = "idx_orders_status", columnList = "status"),
    @Index(name = "idx_orders_created_at", columnList = "created_at")
})
public class OrderEntity {
        public OrderResponse toOrderResponse() {
            return new OrderResponse(
                this.id,
                this.agentId,
                this.symbol,
                this.direction != null ? this.direction.name() : null,
                this.price,
                this.quantity,
                this.stopLoss,
                this.takeProfit,
                this.leverage,
                this.status != null ? this.status.name() : null,
                this.createdAt,
                this.executedAt,
                this.exchangeOrderId,
                this.failureReason
            );
        }
    
    @Id
    private String id;

    @NotBlank
    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @NotBlank
    @Column(nullable = false)
    private String symbol;

    @NotNull
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Direction direction;

    @Positive
    @Column(nullable = false)
    private double price;

    @Positive
    @Column(nullable = false)
    private double quantity;
    
    @Column(name = "stop_loss")
    private Double stopLoss;
    
    @Column(name = "take_profit")
    private Double takeProfit;
    
    @Column
    private Integer leverage;
    
    @NotNull
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;
    
    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "executed_at")
    private Instant executedAt;
    
    @Column(name = "exchange_order_id")
    private String exchangeOrderId;
    
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
    
    // Constructors
    public OrderEntity() {}
    
    public OrderEntity(String id, String agentId, String symbol, Direction direction,
                      double price, double quantity, Double stopLoss, Double takeProfit,
                      Integer leverage, Status status, Instant createdAt) {
        this.id = id;
        this.agentId = agentId;
        this.symbol = symbol;
        this.direction = direction;
        this.price = price;
        this.quantity = quantity;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.leverage = leverage;
        this.status = status;
        this.createdAt = createdAt;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    
    public Double getStopLoss() { return stopLoss; }
    public void setStopLoss(Double stopLoss) { this.stopLoss = stopLoss; }
    
    public Double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(Double takeProfit) { this.takeProfit = takeProfit; }
    
    public Integer getLeverage() { return leverage; }
    public void setLeverage(Integer leverage) { this.leverage = leverage; }
    
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    
    public String getExchangeOrderId() { return exchangeOrderId; }
    public void setExchangeOrderId(String exchangeOrderId) { this.exchangeOrderId = exchangeOrderId; }
    
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    
    @Column(name = "realized_pnl")
    private Double realizedPnl;
    
    public Double getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(Double realizedPnl) { this.realizedPnl = realizedPnl; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String agentId;
        private String symbol;
        private Direction direction;
        private double price;
        private double quantity;
        private Double stopLoss;
        private Double takeProfit;
        private Integer leverage;
        private Status status;
        private Instant createdAt;
        private Instant executedAt;
        private String exchangeOrderId;
        private String failureReason;
        private Double realizedPnl;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder symbol(String symbol) { this.symbol = symbol; return this; }
        public Builder direction(Direction direction) { this.direction = direction; return this; }
        public Builder price(double price) { this.price = price; return this; }
        public Builder quantity(double quantity) { this.quantity = quantity; return this; }
        public Builder stopLoss(Double stopLoss) { this.stopLoss = stopLoss; return this; }
        public Builder takeProfit(Double takeProfit) { this.takeProfit = takeProfit; return this; }
        public Builder leverage(Integer leverage) { this.leverage = leverage; return this; }
        public Builder status(Status status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder executedAt(Instant executedAt) { this.executedAt = executedAt; return this; }
        public Builder exchangeOrderId(String exchangeOrderId) { this.exchangeOrderId = exchangeOrderId; return this; }
        public Builder failureReason(String failureReason) { this.failureReason = failureReason; return this; }
        public Builder realizedPnl(Double realizedPnl) { this.realizedPnl = realizedPnl; return this; }

        public OrderEntity build() {
              OrderEntity order = new OrderEntity();
              order.setId(id);
              order.setAgentId(agentId);
              order.setSymbol(symbol);
              order.setDirection(direction);
              order.setPrice(price);
              order.setQuantity(quantity);
              order.setStopLoss(stopLoss);
              order.setTakeProfit(takeProfit);
              order.setLeverage(leverage);
              order.setStatus(status);
              order.setCreatedAt(createdAt != null ? createdAt : Instant.now());
              order.setExecutedAt(executedAt);
              order.setExchangeOrderId(exchangeOrderId);
              order.setFailureReason(failureReason);
              order.setRealizedPnl(realizedPnl);
              return order;
        }
    }
    
    // Enums
    public enum Direction {
        LONG, SHORT
    }
    
    public enum Status {
        PENDING, SUBMITTED, EXECUTED, FAILED, CANCELLED
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("OrderEntity{");
        sb.append("id='").append(id).append("', ");
        sb.append("agentId='").append(agentId).append("', ");
        sb.append("symbol='").append(symbol).append("', ");
        sb.append("direction=").append(direction != null ? direction.name() : null).append(", ");
        sb.append("price=").append(price).append(", ");
        sb.append("quantity=").append(quantity).append(", ");
        sb.append("stopLoss=").append(stopLoss).append(", ");
        sb.append("takeProfit=").append(takeProfit).append(", ");
        sb.append("leverage=").append(leverage).append(", ");
        sb.append("status=").append(status != null ? status.name() : null).append(", ");
        sb.append("createdAt=").append(createdAt).append(", ");
        sb.append("executedAt=").append(executedAt).append(", ");
        sb.append("exchangeOrderId='").append(exchangeOrderId).append("', ");
        sb.append("failureReason='").append(failureReason).append("', ");
        sb.append("realizedPnl=").append(realizedPnl);
        sb.append("}");
        return sb.toString();
    }
}
