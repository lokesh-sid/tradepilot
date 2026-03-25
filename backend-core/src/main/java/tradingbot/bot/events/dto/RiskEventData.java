package tradingbot.bot.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event data for risk events.
 */
public class RiskEventData {
    
    @JsonProperty("agentId")
    private String agentId;
    
    @JsonProperty("riskType")
    private String riskType;
    
    @JsonProperty("severity")
    private String severity;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("currentExposure")
    private Double currentExposure;
    
    @JsonProperty("maxExposure")
    private Double maxExposure;
    
    @JsonProperty("drawdown")
    private Double drawdown;
    
    @JsonProperty("actionRequired")
    private Boolean actionRequired;
    
    public String getAgentId() {
        return agentId;
    }
    
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
    
    public String getRiskType() {
        return riskType;
    }
    
    public void setRiskType(String riskType) {
        this.riskType = riskType;
    }
    
    public String getSeverity() {
        return severity;
    }
    
    public void setSeverity(String severity) {
        this.severity = severity;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Double getCurrentExposure() {
        return currentExposure;
    }
    
    public void setCurrentExposure(Double currentExposure) {
        this.currentExposure = currentExposure;
    }
    
    public Double getMaxExposure() {
        return maxExposure;
    }
    
    public void setMaxExposure(Double maxExposure) {
        this.maxExposure = maxExposure;
    }
    
    public Double getDrawdown() {
        return drawdown;
    }
    
    public void setDrawdown(Double drawdown) {
        this.drawdown = drawdown;
    }
    
    public Boolean getActionRequired() {
        return actionRequired;
    }
    
    public void setActionRequired(Boolean actionRequired) {
        this.actionRequired = actionRequired;
    }
    
    @Override
    public String toString() {
        return "RiskEventData{" +
                "agentId='" + agentId + '\'' +
                ", riskType='" + riskType + '\'' +
                ", severity='" + severity + '\'' +
                ", message='" + message + '\'' +
                ", currentExposure=" + currentExposure +
                ", maxExposure=" + maxExposure +
                ", drawdown=" + drawdown +
                ", actionRequired=" + actionRequired +
                '}';
    }
}
