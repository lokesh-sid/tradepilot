package tradingbot.bot.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA Entity for storing risk events.
 */
@Entity
@Table(name = "risk_events")
public class RiskEventEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "risk_type")
    private String riskType;
    
    @Column(name = "risk_level")
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "current_price")
    private Double currentPrice;
    
    @Column(name = "trigger_value")
    private Double triggerValue;
    
    @Column(name = "action_taken")
    private String actionTaken;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getRiskType() {
        return riskType;
    }
    
    public void setRiskType(String riskType) {
        this.riskType = riskType;
    }
    
    public String getRiskLevel() {
        return riskLevel;
    }
    
    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Double getCurrentPrice() {
        return currentPrice;
    }
    
    public void setCurrentPrice(Double currentPrice) {
        this.currentPrice = currentPrice;
    }
    
    public Double getTriggerValue() {
        return triggerValue;
    }
    
    public void setTriggerValue(Double triggerValue) {
        this.triggerValue = triggerValue;
    }
    
    public String getActionTaken() {
        return actionTaken;
    }
    
    public void setActionTaken(String actionTaken) {
        this.actionTaken = actionTaken;
    }
}
