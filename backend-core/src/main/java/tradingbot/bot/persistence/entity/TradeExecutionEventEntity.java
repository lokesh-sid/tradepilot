package tradingbot.bot.persistence.entity;

import jakarta.persistence.*;

/**
 * JPA Entity for storing trade execution events.
 */
@Entity
@DiscriminatorValue("TRADE_EXECUTED")
public class TradeExecutionEventEntity extends TradingEventEntity {
    
    @Column(name = "order_id")
    private String orderId;
    
    @Column(name = "trade_id")
    private String tradeId;
    
    @Column(name = "side")
    private String side; // BUY, SELL, CLOSE
    
    @Column(name = "quantity")
    private Double quantity;
    
    @Column(name = "price")
    private Double price;
    
    @Column(name = "status")
    private String status; // FILLED, PARTIAL, REJECTED
    
    @Column(name = "profit_loss")
    private Double profitLoss;
    
    @Column(name = "commission")
    private Double commission;
    
    // Getters and Setters
    public String getOrderId() {
        return orderId;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    public String getTradeId() {
        return tradeId;
    }
    
    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }
    
    public String getSide() {
        return side;
    }
    
    public void setSide(String side) {
        this.side = side;
    }
    
    public Double getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }
    
    public Double getPrice() {
        return price;
    }
    
    public void setPrice(Double price) {
        this.price = price;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Double getProfitLoss() {
        return profitLoss;
    }
    
    public void setProfitLoss(Double profitLoss) {
        this.profitLoss = profitLoss;
    }
    
    public Double getCommission() {
        return commission;
    }
    
    public void setCommission(Double commission) {
        this.commission = commission;
    }
}
