package tradingbot.bot.persistence.entity;

import jakarta.persistence.*;

/**
 * JPA Entity for storing trade signal events.
 */
@Entity
@DiscriminatorValue("TRADE_SIGNAL")
public class TradeSignalEventEntity extends TradingEventEntity {
    
    @Column(name = "signal_direction")
    private String signalDirection;
    
    @Column(name = "confidence")
    private Double confidence;
    
    @Column(name = "current_price")
    private Double currentPrice;
    
    @Column(name = "indicators", length = 2000)
    private String indicators; // JSON string of indicators
    
    // Getters and Setters
    public String getSignalDirection() {
        return signalDirection;
    }
    
    public void setSignalDirection(String signalDirection) {
        this.signalDirection = signalDirection;
    }
    
    public Double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
    
    public Double getCurrentPrice() {
        return currentPrice;
    }
    
    public void setCurrentPrice(Double currentPrice) {
        this.currentPrice = currentPrice;
    }
    
    public String getIndicators() {
        return indicators;
    }
    
    public void setIndicators(String indicators) {
        this.indicators = indicators;
    }
}
