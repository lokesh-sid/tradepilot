package tradingbot.bot.service;

import java.time.Instant;

/**
 * OrderResult - Result of order execution from exchange
 * 
 * Contains real order details returned by the exchange after placing an order.
 * This replaces fake order IDs with actual exchange order tracking.
 */
public class OrderResult {
    
    private final String exchangeOrderId;    // Real order ID from exchange
    private final String clientOrderId;      // Our internal ID (if provided)
    private final String symbol;
    private final String side;               // Buy or Sell
    private final OrderStatus status;
    private final double orderedQuantity;
    private final double filledQuantity;
    private final double avgFillPrice;
    private final double commission;
    private final Instant createdAt;
    private final Instant updatedAt;
    
    private OrderResult(Builder builder) {
        this.exchangeOrderId = builder.exchangeOrderId;
        this.clientOrderId = builder.clientOrderId;
        this.symbol = builder.symbol;
        this.side = builder.side;
        this.status = builder.status;
        this.orderedQuantity = builder.orderedQuantity;
        this.filledQuantity = builder.filledQuantity;
        this.avgFillPrice = builder.avgFillPrice;
        this.commission = builder.commission;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getExchangeOrderId() { return exchangeOrderId; }
    public String getClientOrderId() { return clientOrderId; }
    public String getSymbol() { return symbol; }
    public String getSide() { return side; }
    public OrderStatus getStatus() { return status; }
    public double getOrderedQuantity() { return orderedQuantity; }
    public double getFilledQuantity() { return filledQuantity; }
    public double getAvgFillPrice() { return avgFillPrice; }
    public double getCommission() { return commission; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    
    public enum OrderStatus {
        NEW,              // Order created but not yet filled
        PARTIALLY_FILLED, // Order partially filled
        FILLED,           // Order completely filled
        CANCELED,         // Order canceled
        REJECTED,         // Order rejected by exchange
        EXPIRED           // Order expired
    }
    
    public static class Builder {
        private String exchangeOrderId;
        private String clientOrderId;
        private String symbol;
        private String side;
        private OrderStatus status;
        private double orderedQuantity;
        private double filledQuantity;
        private double avgFillPrice;
        private double commission;
        private Instant createdAt;
        private Instant updatedAt;
        
        public Builder exchangeOrderId(String exchangeOrderId) {
            this.exchangeOrderId = exchangeOrderId;
            return this;
        }
        
        public Builder clientOrderId(String clientOrderId) {
            this.clientOrderId = clientOrderId;
            return this;
        }
        
        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }
        
        public Builder side(String side) {
            this.side = side;
            return this;
        }
        
        public Builder status(OrderStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder orderedQuantity(double orderedQuantity) {
            this.orderedQuantity = orderedQuantity;
            return this;
        }
        
        public Builder filledQuantity(double filledQuantity) {
            this.filledQuantity = filledQuantity;
            return this;
        }
        
        public Builder avgFillPrice(double avgFillPrice) {
            this.avgFillPrice = avgFillPrice;
            return this;
        }
        
        public Builder commission(double commission) {
            this.commission = commission;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }
        
        public OrderResult build() {
            return new OrderResult(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("OrderResult{exchangeOrderId='%s', symbol='%s', side='%s', status=%s, qty=%.4f, filled=%.4f, avgPrice=%.4f}",
            exchangeOrderId, symbol, side, status, orderedQuantity, filledQuantity, avgFillPrice);
    }
}
