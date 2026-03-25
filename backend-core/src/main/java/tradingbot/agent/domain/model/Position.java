package tradingbot.agent.domain.model;

import java.time.Instant;

/**
 * Position - Represents an open trading position
 * 
 * Tracks an active position on the exchange with entry/exit details,
 * P&L calculations, and stop-loss/take-profit order IDs.
 */
public class Position {
    
    private final String id;
    private final String agentId;
    private final String symbol;
    private final TradeDirection direction;
    private final double entryPrice;
    private final double quantity;
    private final Double stopLoss;
    private final Double takeProfit;
    private final String mainOrderId;          // Exchange order ID of entry order
    private String stopLossOrderId;            // Exchange order ID of stop-loss order
    private String takeProfitOrderId;          // Exchange order ID of take-profit order
    private PositionStatus status;
    private Double exitPrice;
    private Double realizedPnl;
    private Instant openedAt;
    private Instant closedAt;
    private Instant lastCheckedAt;             // Last time position was monitored
    private double lastUnrealizedPnl;          // Last calculated unrealized P&L
    
    private Position(Builder builder) {
        this.id = builder.id;
        this.agentId = builder.agentId;
        this.symbol = builder.symbol;
        this.direction = builder.direction;
        this.entryPrice = builder.entryPrice;
        this.quantity = builder.quantity;
        this.stopLoss = builder.stopLoss;
        this.takeProfit = builder.takeProfit;
        this.mainOrderId = builder.mainOrderId;
        this.stopLossOrderId = builder.stopLossOrderId;
        this.takeProfitOrderId = builder.takeProfitOrderId;
        this.status = builder.status;
        this.exitPrice = builder.exitPrice;
        this.realizedPnl = builder.realizedPnl;
        this.openedAt = builder.openedAt;
        this.closedAt = builder.closedAt;
        this.lastCheckedAt = builder.lastCheckedAt;
        this.lastUnrealizedPnl = builder.lastUnrealizedPnl;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getId() { return id; }
    public String getAgentId() { return agentId; }
    public String getSymbol() { return symbol; }
    public TradeDirection getDirection() { return direction; }
    public double getEntryPrice() { return entryPrice; }
    public double getQuantity() { return quantity; }
    public Double getStopLoss() { return stopLoss; }
    public Double getTakeProfit() { return takeProfit; }
    public String getMainOrderId() { return mainOrderId; }
    public String getStopLossOrderId() { return stopLossOrderId; }
    public String getTakeProfitOrderId() { return takeProfitOrderId; }
    public PositionStatus getStatus() { return status; }
    public Double getExitPrice() { return exitPrice; }
    public Double getRealizedPnl() { return realizedPnl; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public double getLastUnrealizedPnl() { return lastUnrealizedPnl; }
    
    // Mutable state for lifecycle transitions
    public void updateMonitoring(double unrealizedPnl) {
        this.lastUnrealizedPnl = unrealizedPnl;
        this.lastCheckedAt = Instant.now();
    }
    
    public void setStopLossOrderId(String stopLossOrderId) {
        this.stopLossOrderId = stopLossOrderId;
    }
    
    public void setTakeProfitOrderId(String takeProfitOrderId) {
        this.takeProfitOrderId = takeProfitOrderId;
    }
    
    public void close(double exitPrice, double realizedPnl, PositionStatus status) {
        this.exitPrice = exitPrice;
        this.realizedPnl = realizedPnl;
        this.status = status;
        this.closedAt = Instant.now();
    }
    
    /**
     * Calculate unrealized P&L based on current price
     */
    public double calculateUnrealizedPnl(double currentPrice) {
        if (direction == TradeDirection.LONG) {
            return (currentPrice - entryPrice) * quantity;
        } else { // SHORT
            return (entryPrice - currentPrice) * quantity;
        }
    }
    
    /**
     * Calculate unrealized P&L percentage
     */
    public double calculateUnrealizedPnlPercent(double currentPrice) {
        double pnl = calculateUnrealizedPnl(currentPrice);
        double investment = entryPrice * quantity;
        return (pnl / investment) * 100.0;
    }
    
    public enum PositionStatus {
        OPEN,           // Position is active
        CLOSED,         // Position closed normally (take-profit or manual exit)
        STOPPED_OUT,    // Position closed by stop-loss
        LIQUIDATED      // Position liquidated by exchange
    }
    
    public static class Builder {
        private String id;
        private String agentId;
        private String symbol;
        private TradeDirection direction;
        private double entryPrice;
        private double quantity;
        private Double stopLoss;
        private Double takeProfit;
        private String mainOrderId;
        private String stopLossOrderId;
        private String takeProfitOrderId;
        private PositionStatus status = PositionStatus.OPEN;
        private Double exitPrice;
        private Double realizedPnl;
        private Instant openedAt = Instant.now();
        private Instant closedAt;
        private Instant lastCheckedAt = Instant.now();
        private double lastUnrealizedPnl = 0.0;
        
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
        
        public Builder entryPrice(double entryPrice) {
            this.entryPrice = entryPrice;
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
        
        public Builder mainOrderId(String mainOrderId) {
            this.mainOrderId = mainOrderId;
            return this;
        }
        
        public Builder stopLossOrderId(String stopLossOrderId) {
            this.stopLossOrderId = stopLossOrderId;
            return this;
        }
        
        public Builder takeProfitOrderId(String takeProfitOrderId) {
            this.takeProfitOrderId = takeProfitOrderId;
            return this;
        }
        
        public Builder status(PositionStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder exitPrice(Double exitPrice) {
            this.exitPrice = exitPrice;
            return this;
        }
        
        public Builder realizedPnl(Double realizedPnl) {
            this.realizedPnl = realizedPnl;
            return this;
        }
        
        public Builder openedAt(Instant openedAt) {
            this.openedAt = openedAt;
            return this;
        }
        
        public Builder closedAt(Instant closedAt) {
            this.closedAt = closedAt;
            return this;
        }
        
        public Builder lastCheckedAt(Instant lastCheckedAt) {
            this.lastCheckedAt = lastCheckedAt;
            return this;
        }
        
        public Builder lastUnrealizedPnl(double lastUnrealizedPnl) {
            this.lastUnrealizedPnl = lastUnrealizedPnl;
            return this;
        }
        
        public Position build() {
            return new Position(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("Position{id='%s', symbol='%s', direction=%s, entry=%.2f, qty=%.4f, status=%s, unrealizedPnl=%.2f}",
            id, symbol, direction, entryPrice, quantity, status, lastUnrealizedPnl);
    }
}
