package tradingbot.agent.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Order - A trade execution command from an agent
 * 
 * This domain object represents an order to be placed on the exchange.
 * It is created by the OrderPlacementService after the agent's reasoning
 * indicates a trade should be executed.
 */
public class Order {
    
    private final String id;
    private final String agentId;
    private final String symbol;
    private final TradeDirection direction;
    private final double price;
    private final double quantity;
    private final Double stopLoss;    // Optional - null if not set
    private final Double takeProfit;  // Optional - null if not set
    private final Integer leverage;   // Optional - null for spot trading
    private OrderStatus status;
    private Instant createdAt;
    private Instant executedAt;
    private String exchangeOrderId;  // ID returned by exchange after execution
    private String failureReason;
    
    private Order(Builder builder) {
        this.id = builder.id;
        this.agentId = builder.agentId;
        this.symbol = builder.symbol;
        this.direction = builder.direction;
        this.price = builder.price;
        this.quantity = builder.quantity;
        this.stopLoss = builder.stopLoss;
        this.takeProfit = builder.takeProfit;
        this.leverage = builder.leverage;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.executedAt = builder.executedAt;
        this.exchangeOrderId = builder.exchangeOrderId;
        this.failureReason = builder.failureReason;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getId() { return id; }
    public String getAgentId() { return agentId; }
    public String getSymbol() { return symbol; }
    public TradeDirection getDirection() { return direction; }
    public double getPrice() { return price; }
    public double getQuantity() { return quantity; }
    public Double getStopLoss() { return stopLoss; }
    public Double getTakeProfit() { return takeProfit; }
    public Integer getLeverage() { return leverage; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExecutedAt() { return executedAt; }
    public String getExchangeOrderId() { return exchangeOrderId; }
    public String getFailureReason() { return failureReason; }
    
    // Mutable state for lifecycle transitions
    public void markExecuted(String exchangeOrderId, Instant executedAt) {
        this.status = OrderStatus.EXECUTED;
        this.exchangeOrderId = exchangeOrderId;
        this.executedAt = executedAt;
    }
    
    public void markFailed(String failureReason) {
        this.status = OrderStatus.FAILED;
        this.failureReason = failureReason;
    }
    
    public void markCancelled() {
        this.status = OrderStatus.CANCELLED;
    }
    
    /**
     * Check if order has risk management parameters
     */
    public boolean hasRiskManagement() {
        return stopLoss != null || takeProfit != null;
    }
    
    /**
     * Check if order is for futures trading
     */
    public boolean isFutures() {
        return leverage != null && leverage > 1;
    }
    
    /**
     * Calculate position size in base currency
     */
    public double getPositionSize() {
        return price * quantity;
    }
    
    /**
     * Calculate potential loss if stop loss is hit
     */
    public Double calculateMaxLoss() {
        if (stopLoss == null) return null;
        
        double lossPerUnit = Math.abs(price - stopLoss);
        return lossPerUnit * quantity;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(id, order.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Order{" +
                "id='" + id + '\'' +
                ", symbol='" + symbol + '\'' +
                ", direction=" + direction +
                ", price=" + price +
                ", quantity=" + quantity +
                ", status=" + status +
                '}';
    }
    
    /**
     * OrderStatus - Enum representing the lifecycle of an order
     */
    public enum OrderStatus {
        PENDING,    // Order created, not yet sent to exchange
        SUBMITTED,  // Order sent to exchange, awaiting confirmation
        EXECUTED,   // Order successfully executed
        FAILED,     // Order failed (insufficient balance, invalid params, etc)
        CANCELLED   // Order cancelled before execution
    }
    
    /**
     * Builder for Order
     */
    public static class Builder {
        private String id;
        private String agentId;
        private String symbol;
        private TradeDirection direction;
        private double price;
        private double quantity;
        private Double stopLoss;
        private Double takeProfit;
        private Integer leverage;
        private OrderStatus status = OrderStatus.PENDING;
        private Instant createdAt = Instant.now();
        private Instant executedAt;
        private String exchangeOrderId;
        private String failureReason;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }
        
        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }
        
        public Builder direction(TradeDirection direction) {
            this.direction = direction;
            return this;
        }
        
        public Builder price(double price) {
            this.price = price;
            return this;
        }
        
        public Builder quantity(double quantity) {
            this.quantity = quantity;
            return this;
        }
        
        public Builder stopLoss(Double stopLoss) {
            this.stopLoss = stopLoss;
            return this;
        }
        
        public Builder takeProfit(Double takeProfit) {
            this.takeProfit = takeProfit;
            return this;
        }
        
        public Builder leverage(Integer leverage) {
            this.leverage = leverage;
            return this;
        }
        
        public Builder status(OrderStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder executedAt(Instant executedAt) {
            this.executedAt = executedAt;
            return this;
        }
        
        public Builder exchangeOrderId(String exchangeOrderId) {
            this.exchangeOrderId = exchangeOrderId;
            return this;
        }
        
        public Builder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }
        
        public Order build() {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(agentId, "agentId must not be null");
            Objects.requireNonNull(symbol, "symbol must not be null");
            Objects.requireNonNull(direction, "direction must not be null");
            
            if (price <= 0) {
                throw new IllegalArgumentException("price must be positive");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            
            return new Order(this);
        }
    }
}
