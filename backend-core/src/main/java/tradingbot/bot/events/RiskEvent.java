package tradingbot.bot.events;

/**
 * Event representing a risk management alert or action.
 * Published when risk conditions are detected or risk actions are taken.
 */
public class RiskEvent extends TradingEvent {
    
    private String riskType; // TRAILING_STOP_TRIGGERED, LIQUIDATION_RISK, MARGIN_CALL, etc.
    private String symbol;
    private double currentPrice;
    private double stopPrice;
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private String action; // CLOSE_POSITION, REDUCE_LEVERAGE, ALERT_ONLY
    private String description;
    
    public RiskEvent() {
        super();
    }
    
    public RiskEvent(String botId, String riskType, String symbol) {
        super(botId, "RISK_ALERT");
        this.riskType = riskType;
        this.symbol = symbol;
    }
    
    // Getters and Setters
    public String getRiskType() {
        return riskType;
    }
    
    public void setRiskType(String riskType) {
        this.riskType = riskType;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public double getCurrentPrice() {
        return currentPrice;
    }
    
    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }
    
    public double getStopPrice() {
        return stopPrice;
    }
    
    public void setStopPrice(double stopPrice) {
        this.stopPrice = stopPrice;
    }
    
    public String getSeverity() {
        return severity;
    }
    
    public void setSeverity(String severity) {
        this.severity = severity;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return "RiskEvent{" +
                "riskType='" + riskType + '\'' +
                ", symbol='" + symbol + '\'' +
                ", currentPrice=" + currentPrice +
                ", severity='" + severity + '\'' +
                ", action='" + action + '\'' +
                ", eventId='" + getEventId() + '\'' +
                '}';
    }
}