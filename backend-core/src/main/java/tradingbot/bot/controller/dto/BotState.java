package tradingbot.bot.controller.dto;

import java.io.Serializable;
import java.time.Instant;

import tradingbot.config.TradingConfig;

/**
 * Bot State DTO for Redis caching
 * 
 * This class represents the persistent state of a trading bot that can be
 * serialized and stored in Redis for recovery and horizontal scaling.
 */
public class BotState implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String botId;
    private String direction;
    private boolean paper;
    private boolean running;
    private TradingConfig config;
    private boolean sentimentEnabled;
    private double currentLeverage;
    private Instant createdAt;
    private Instant lastUpdated;
    private Double entryPrice;
    private String positionStatus;
    
    // Constructors
    public BotState() {
    }
    
    private BotState(Builder builder) {
        this.botId = builder.botId;
        this.direction = builder.direction;
        this.paper = builder.paper;
        this.running = builder.running;
        this.config = builder.config;
        this.sentimentEnabled = builder.sentimentEnabled;
        this.currentLeverage = builder.currentLeverage;
        this.createdAt = builder.createdAt;
        this.lastUpdated = builder.lastUpdated;
        this.entryPrice = builder.entryPrice;
        this.positionStatus = builder.positionStatus;
    }
    
    // Getters and Setters
    public String getBotId() {
        return botId;
    }
    
    public void setBotId(String botId) {
        this.botId = botId;
    }
    
    public String getDirection() {
        return direction;
    }
    
    public void setDirection(String direction) {
        this.direction = direction;
    }
    
    public boolean isPaper() {
        return paper;
    }
    
    public void setPaper(boolean paper) {
        this.paper = paper;
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public void setRunning(boolean running) {
        this.running = running;
    }
    
    public TradingConfig getConfig() {
        return config;
    }
    
    public void setConfig(TradingConfig config) {
        this.config = config;
    }
    
    public boolean isSentimentEnabled() {
        return sentimentEnabled;
    }
    
    public void setSentimentEnabled(boolean sentimentEnabled) {
        this.sentimentEnabled = sentimentEnabled;
    }
    
    public double getCurrentLeverage() {
        return currentLeverage;
    }
    
    public void setCurrentLeverage(double currentLeverage) {
        this.currentLeverage = currentLeverage;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    public Double getEntryPrice() {
        return entryPrice;
    }
    
    public void setEntryPrice(Double entryPrice) {
        this.entryPrice = entryPrice;
    }
    
    public String getPositionStatus() {
        return positionStatus;
    }
    
    public void setPositionStatus(String positionStatus) {
        this.positionStatus = positionStatus;
    }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String botId;
        private String direction;
        private boolean paper;
        private boolean running;
        private TradingConfig config;
        private boolean sentimentEnabled;
        private double currentLeverage;
        private Instant createdAt;
        private Instant lastUpdated;
        private Double entryPrice;
        private String positionStatus;
        
        public Builder botId(String botId) {
            this.botId = botId;
            return this;
        }
        
        public Builder direction(String direction) {
            this.direction = direction;
            return this;
        }
        
        public Builder paper(boolean paper) {
            this.paper = paper;
            return this;
        }
        
        public Builder running(boolean running) {
            this.running = running;
            return this;
        }
        
        public Builder config(TradingConfig config) {
            this.config = config;
            return this;
        }
        
        public Builder sentimentEnabled(boolean sentimentEnabled) {
            this.sentimentEnabled = sentimentEnabled;
            return this;
        }
        
        public Builder currentLeverage(double currentLeverage) {
            this.currentLeverage = currentLeverage;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder lastUpdated(Instant lastUpdated) {
            this.lastUpdated = lastUpdated;
            return this;
        }
        
        public Builder entryPrice(Double entryPrice) {
            this.entryPrice = entryPrice;
            return this;
        }
        
        public Builder positionStatus(String positionStatus) {
            this.positionStatus = positionStatus;
            return this;
        }
        
        public BotState build() {
            return new BotState(this);
        }
    }
}
