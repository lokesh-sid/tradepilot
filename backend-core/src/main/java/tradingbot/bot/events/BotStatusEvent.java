package tradingbot.bot.events;

/**
 * Event representing bot status changes.
 * Published when bot state changes (started, stopped, configured, etc.).
 */
public class BotStatusEvent extends TradingEvent {
    
    private String status; // STARTED, STOPPED, RUNNING, ERROR, CONFIGURED
    private String previousStatus;
    private String message;
    private String configurationHash; // Hash of current configuration
    private boolean isRunning;
    private double currentBalance;
    private String activePosition; // LONG, SHORT, NONE
    private double entryPrice;
    
    public BotStatusEvent() {
        super();
    }
    
    public BotStatusEvent(String botId, String status) {
        super(botId, "BOT_STATUS");
        this.status = status;
    }
    
    // Getters and Setters
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getPreviousStatus() {
        return previousStatus;
    }
    
    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getConfigurationHash() {
        return configurationHash;
    }
    
    public void setConfigurationHash(String configurationHash) {
        this.configurationHash = configurationHash;
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public void setRunning(boolean running) {
        isRunning = running;
    }
    
    public double getCurrentBalance() {
        return currentBalance;
    }
    
    public void setCurrentBalance(double currentBalance) {
        this.currentBalance = currentBalance;
    }
    
    public String getActivePosition() {
        return activePosition;
    }
    
    public void setActivePosition(String activePosition) {
        this.activePosition = activePosition;
    }
    
    public double getEntryPrice() {
        return entryPrice;
    }
    
    public void setEntryPrice(double entryPrice) {
        this.entryPrice = entryPrice;
    }
    
    @Override
    public String toString() {
        return "BotStatusEvent{" +
                "status='" + status + '\'' +
                ", previousStatus='" + previousStatus + '\'' +
                ", isRunning=" + isRunning +
                ", activePosition='" + activePosition + '\'' +
                ", eventId='" + getEventId() + '\'' +
                '}';
    }
}