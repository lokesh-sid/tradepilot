package tradingbot.bot.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tradingbot.bot.events.MarketDataEvent;
import tradingbot.bot.events.RiskEvent;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.strategy.tracker.TrailingStopTracker;

/**
 * Event-driven version of the TrailingStopTracker that demonstrates
 * how to refactor existing components to use message-driven architecture.
 * 
 * This version publishes risk events when trailing stops are triggered,
 * allowing for better monitoring and potentially automatic position management.
 */
@Component
@ConditionalOnProperty(name = "trading.events.enabled", havingValue = "true", matchIfMissing = false)
public class EventDrivenTrailingStopTracker {
    
    private static final Logger log = LoggerFactory.getLogger(EventDrivenTrailingStopTracker.class);
    
    private final FuturesExchangeService exchangeService;
    private final EventPublisher eventPublisher;
    private final double trailingStopPercent;
    private String botId;
    private String position;
    private double entryPrice;
    private double highestPrice;
    
    public EventDrivenTrailingStopTracker(FuturesExchangeService exchangeService,
                                        EventPublisher eventPublisher) {
        this.exchangeService = exchangeService;
        this.eventPublisher = eventPublisher;
        this.trailingStopPercent = 2.0; // Default 2% trailing stop
    }
    
    /**
     * Initializes the trailing stop and publishes a status event.
     * 
     * @param botId The bot ID for event correlation
     * @param price The initial price to start tracking
     */
    public void initializeTrailingStop(String botId, double price) {
        this.botId = botId;
        this.highestPrice = price;
        this.entryPrice = price;
        this.position = "long";
        
        log.info("Trailing stop initialized at {} for bot: {}", price, botId);
        
        // Publish initialization event for monitoring
        publishTrailingStopEvent("TRAILING_STOP_INITIALIZED", price, price, "LOW", "MONITOR_ONLY",
            "Trailing stop initialized at price: " + price);
    }
    
    /**
     * Resets the trailing stop tracker and publishes a status event.
     */
    public void reset() {
        log.info("Resetting trailing stop tracker for bot: {}", botId);
        
        if (botId != null) {
            publishTrailingStopEvent("TRAILING_STOP_RESET", 0.0, 0.0, "LOW", "MONITOR_ONLY",
                "Trailing stop tracker has been reset");
        }
        
        this.highestPrice = 0.0;
        this.entryPrice = 0.0;
        this.position = null;
        this.botId = null;
    }
    
    /**
     * Updates the trailing stop based on market data events.
     * This method would typically be called by a market data event consumer.
     * 
     * @param marketDataEvent Market data event containing price updates
     */
    public void handleMarketDataEvent(MarketDataEvent marketDataEvent) {
        if (botId == null || !botId.equals(marketDataEvent.getBotId())) {
            return; // Not our bot
        }
        
        updateTrailingStop(marketDataEvent.getPrice());
        
        // Check if trailing stop should be triggered
        if (checkTrailingStop(marketDataEvent.getPrice())) {
            // Trigger handled by publishing risk event
            log.info("Trailing stop triggered for bot: {} at price: {}", botId, marketDataEvent.getPrice());
        }
    }
    
    /**
     * Updates the trailing stop price if current price is higher.
     * 
     * @param price Current market price
     */
    public void updateTrailingStop(double price) {
        if ("long".equals(position) && price > highestPrice) {
            double previousHigh = highestPrice;
            highestPrice = price;
            
            log.debug("Updated trailing stop for bot: {} - New highest price: {} (was: {})", 
                botId, highestPrice, previousHigh);
            
            // Publish update event for monitoring (low priority)
            publishTrailingStopEvent("TRAILING_STOP_UPDATED", price, calculateStopPrice(), "LOW", "MONITOR_ONLY",
                String.format("Trailing stop updated: highest=%.2f, stop=%.2f", highestPrice, calculateStopPrice()));
        }
    }
    
    /**
     * Checks if the trailing stop should be triggered and publishes risk event if so.
     * 
     * @param price Current market price
     * @return true if trailing stop was triggered
     */
    public boolean checkTrailingStop(double price) {
        if (!"long".equals(position) || highestPrice <= 0) {
            return false;
        }
        
        double stopPrice = calculateStopPrice();
        if (price <= stopPrice) {
            // Publish high-priority risk event for automatic position closure
            publishTrailingStopEvent("TRAILING_STOP_TRIGGERED", price, stopPrice, "HIGH", "CLOSE_POSITION",
                String.format("Trailing stop triggered! Current price: %.2f, Stop price: %.2f", price, stopPrice));
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Calculates the current trailing stop price.
     * 
     * @return The trailing stop price
     */
    private double calculateStopPrice() {
        return highestPrice * (1 - trailingStopPercent / 100);
    }
    
    /**
     * Publishes a trailing stop related risk event.
     * 
     * @param riskType Type of the risk event
     * @param currentPrice Current market price
     * @param stopPrice Calculated stop price
     * @param severity Event severity
     * @param action Recommended action
     * @param description Event description
     */
    private void publishTrailingStopEvent(String riskType, double currentPrice, double stopPrice, 
                                        String severity, String action, String description) {
        if (botId == null) {
            log.warn("Cannot publish trailing stop event - botId is null");
            return;
        }
        
        try {
            RiskEvent riskEvent = new RiskEvent(botId, riskType, "BTCUSDT"); // Default symbol - would be configurable
            riskEvent.setCurrentPrice(currentPrice);
            riskEvent.setStopPrice(stopPrice);
            riskEvent.setSeverity(severity);
            riskEvent.setAction(action);
            riskEvent.setDescription(description);
            
            // Publish asynchronously
            eventPublisher.publishRiskEvent(riskEvent)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to publish trailing stop event", throwable);
                    } else {
                        log.trace("Successfully published trailing stop event: {}", riskEvent.getEventId());
                    }
                });
                
        } catch (Exception ex) {
            log.error("Error creating trailing stop risk event", ex);
        }
    }
    
    // Getters for monitoring and compatibility
    public double getCurrentPrice() {
        return exchangeService.getCurrentPrice("BTCUSDT");
    }
    
    public double getHighestPrice() {
        return highestPrice;
    }
    
    public String getPosition() {
        return position;
    }
    
    public double getEntryPrice() {
        return entryPrice;
    }
    
    public String getBotId() {
        return botId;
    }
    
    public double getTrailingStopPercent() {
        return trailingStopPercent;
    }
    
    /**
     * Demonstrates integration with the original TrailingStopTracker.
     * This method shows how to wrap the existing tracker with event-driven behavior.
     */
    public static class TrailingStopTrackerEventWrapper {
        private final TrailingStopTracker originalTracker;
        private final EventPublisher eventPublisher;
        private final String botId;
        
        public TrailingStopTrackerEventWrapper(TrailingStopTracker originalTracker, 
                                             EventPublisher eventPublisher, 
                                             String botId) {
            this.originalTracker = originalTracker;
            this.eventPublisher = eventPublisher;
            this.botId = botId;
        }
        
        /**
         * Wraps the original updateTrailingStop method with event publishing.
         */
        public void updateTrailingStop(double price) {
            double previousHigh = originalTracker.getHighestPrice();
            
            // Call original method
            originalTracker.updateTrailingStop(price);
            
            // If highest price changed, publish event
            if (originalTracker.getHighestPrice() > previousHigh) {
                publishUpdateEvent(price);
            }
        }
        
        /**
         * Wraps the original checkTrailingStop method with event publishing.
         */
        public boolean checkTrailingStop(double price) {
            boolean triggered = originalTracker.checkTrailingStop(price);
            
            if (triggered) {
                publishTriggerEvent(price);
            }
            
            return triggered;
        }
        
        private void publishUpdateEvent(double price) {
            // Implementation similar to above...
        }
        
        private void publishTriggerEvent(double price) {
            // Implementation similar to above...
        }
    }
}