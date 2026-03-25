package tradingbot.agent.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * PositionEntity - JPA entity for Position persistence
 */
@Entity
@Table(name = "positions", indexes = {
    @Index(name = "idx_positions_agent_id", columnList = "agent_id"),
    @Index(name = "idx_positions_symbol", columnList = "symbol"),
    @Index(name = "idx_positions_status", columnList = "status"),
    @Index(name = "idx_positions_opened_at", columnList = "opened_at")
})
public class PositionEntity {
    
    @Id
    private String id;
    
    @Column(name = "agent_id", nullable = false)
    private String agentId;
    
    @Column(nullable = false)
    private String symbol;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Direction direction;
    
    @Column(name = "entry_price", nullable = false)
    private double entryPrice;
    
    @Column(nullable = false)
    private double quantity;
    
    @Column(name = "stop_loss")
    private Double stopLoss;
    
    @Column(name = "take_profit")
    private Double takeProfit;
    
    @Column(name = "main_order_id", nullable = false)
    private String mainOrderId;
    
    @Column(name = "stop_loss_order_id")
    private String stopLossOrderId;
    
    @Column(name = "take_profit_order_id")
    private String takeProfitOrderId;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;
    
    @Column(name = "exit_price")
    private Double exitPrice;
    
    @Column(name = "realized_pnl")
    private Double realizedPnl;
    
    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;
    
    @Column(name = "closed_at")
    private Instant closedAt;
    
    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;
    
    @Column(name = "last_unrealized_pnl")
    private double lastUnrealizedPnl;
    
    // Constructors
    public PositionEntity() {}
    
    public PositionEntity(
            String id,
            String agentId,
            String symbol,
            Direction direction,
            double entryPrice,
            double quantity,
            Double stopLoss,
            Double takeProfit,
            String mainOrderId,
            Status status,
            Instant openedAt) {
        this.id = id;
        this.agentId = agentId;
        this.symbol = symbol;
        this.direction = direction;
        this.entryPrice = entryPrice;
        this.quantity = quantity;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.mainOrderId = mainOrderId;
        this.status = status;
        this.openedAt = openedAt;
        this.lastCheckedAt = openedAt;
        this.lastUnrealizedPnl = 0.0;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    
    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }
    
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }
    
    public Double getStopLoss() { return stopLoss; }
    public void setStopLoss(Double stopLoss) { this.stopLoss = stopLoss; }
    
    public Double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(Double takeProfit) { this.takeProfit = takeProfit; }
    
    public String getMainOrderId() { return mainOrderId; }
    public void setMainOrderId(String mainOrderId) { this.mainOrderId = mainOrderId; }
    
    public String getStopLossOrderId() { return stopLossOrderId; }
    public void setStopLossOrderId(String stopLossOrderId) { this.stopLossOrderId = stopLossOrderId; }
    
    public String getTakeProfitOrderId() { return takeProfitOrderId; }
    public void setTakeProfitOrderId(String takeProfitOrderId) { this.takeProfitOrderId = takeProfitOrderId; }
    
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    
    public Double getExitPrice() { return exitPrice; }
    public void setExitPrice(Double exitPrice) { this.exitPrice = exitPrice; }
    
    public Double getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(Double realizedPnl) { this.realizedPnl = realizedPnl; }
    
    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }
    
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(Instant lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    
    public double getLastUnrealizedPnl() { return lastUnrealizedPnl; }
    public void setLastUnrealizedPnl(double lastUnrealizedPnl) { this.lastUnrealizedPnl = lastUnrealizedPnl; }
    
    // Enums
    public enum Direction {
        LONG, SHORT
    }
    
    public enum Status {
        OPEN,
        CLOSED,
        STOPPED_OUT,
        LIQUIDATED
    }
}
