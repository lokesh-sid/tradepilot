package tradingbot.bot.messaging;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import tradingbot.bot.events.BotStatusEvent;
import tradingbot.bot.events.RiskEvent;
import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.events.TradeSignalEvent;
import tradingbot.bot.service.FuturesExchangeService;

/**
 * Event-driven trade execution service that processes trade signals asynchronously.
 * This demonstrates how the current synchronous trading bot can be refactored
 * to use message-driven architecture for better scalability and decoupling.
 * 
 * Note: This is a demonstration implementation. In production, you would use
 * proper Kafka consumers with @KafkaListener annotations.
 */
@Service
public class TradeExecutionService {
    
    private static final Logger log = LoggerFactory.getLogger(TradeExecutionService.class);
    
    private final FuturesExchangeService exchangeService;
    private final EventPublisher eventPublisher;
    private final RiskAssessmentService riskService;
    
    public TradeExecutionService(FuturesExchangeService exchangeService,
                               EventPublisher eventPublisher,
                               RiskAssessmentService riskService) {
        this.exchangeService = exchangeService;
        this.eventPublisher = eventPublisher;
        this.riskService = riskService;
    }
    
    /**
     * Processes trade signal events asynchronously.
     * In production, this would be a @KafkaListener method.
     * 
     * @param signalEvent The trade signal to process
     * @return CompletableFuture for async processing
     */
    @Async
    public CompletableFuture<Void> handleTradeSignal(TradeSignalEvent signalEvent) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Processing trade signal: {} for bot: {} - {} {}", 
                    signalEvent.getEventId(), signalEvent.getBotId(), 
                    signalEvent.getSignal(), signalEvent.getSymbol());
                
                // 1. Risk Assessment
                RiskAssessment riskAssessment = riskService.assessTradeSignal(signalEvent);
                if (!riskAssessment.isApproved()) {
                    publishRiskRejection(signalEvent, riskAssessment);
                    return;
                }
                
                // 2. Calculate Position Size
                double quantity = calculatePositionSize(signalEvent, riskAssessment);
                if (quantity <= 0) {
                    log.warn("Invalid position size calculated for signal: {}", signalEvent.getEventId());
                    return;
                }
                
                // 3. Execute Trade
                TradeExecutionEvent executionEvent = executeTrade(signalEvent, quantity);
                
                // 4. Publish Execution Event
                eventPublisher.publishTradeExecution(executionEvent);
                
                log.info("Successfully processed trade signal: {} - Order ID: {}", 
                    signalEvent.getEventId(), executionEvent.getOrderId());
                
            } catch (Exception ex) {
                log.error("Failed to process trade signal: {}", signalEvent.getEventId(), ex);
                handleTradeExecutionError(signalEvent, ex);
            }
        });
    }
    
    /**
     * Handles bot status events that may affect trade execution.
     * 
     * @param statusEvent The bot status event to process
     * @return CompletableFuture for async processing
     */
    @Async
    public CompletableFuture<Void> handleBotStatusEvent(BotStatusEvent statusEvent) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Processing bot status event: {} - Status: {} for bot: {}", 
                    statusEvent.getEventId(), statusEvent.getStatus(), statusEvent.getBotId());
                
                switch (statusEvent.getStatus().toUpperCase()) {
                    case "STOPPED" -> handleBotStopped(statusEvent);
                    case "ERROR" -> handleBotError(statusEvent);
                    case "STARTED" -> handleBotStarted(statusEvent);
                    case "CONFIGURED" -> handleBotConfigured(statusEvent);
                    case "RUNNING" -> log.debug("Bot {} is running normally", statusEvent.getBotId());
                    default -> log.debug("Bot status update: {} - {}", statusEvent.getStatus(), statusEvent.getMessage());
                }
                
            } catch (Exception ex) {
                log.error("Failed to process bot status event: {}", statusEvent.getEventId(), ex);
            }
        });
    }

    /**
     * Handles risk events that require immediate trade action.
     * 
     * @param riskEvent The risk event to process
     * @return CompletableFuture for async processing
     */
    @Async
    public CompletableFuture<Void> handleRiskEvent(RiskEvent riskEvent) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.warn("Processing risk event: {} - {} for {}", 
                    riskEvent.getEventId(), riskEvent.getRiskType(), riskEvent.getSymbol());
                
                switch (riskEvent.getAction().toUpperCase()) {
                    case "CLOSE_POSITION" -> closePosition(riskEvent);
                    case "REDUCE_LEVERAGE" -> reduceLeverage(riskEvent);
                    case "ALERT_ONLY" -> log.warn("Risk alert: {} - {}", riskEvent.getRiskType(), riskEvent.getDescription());
                    default -> log.warn("Unknown risk action: {}", riskEvent.getAction());
                }
                
            } catch (Exception ex) {
                log.error("Failed to process risk event: {}", riskEvent.getEventId(), ex);
            }
        });
    }
    
    /**
     * Executes a trade based on the signal and calculated quantity.
     * 
     * @param signalEvent The trade signal
     * @param quantity The position size to trade
     * @return TradeExecutionEvent with execution details
     */
    private TradeExecutionEvent executeTrade(TradeSignalEvent signalEvent, double quantity) {
        try {
            String orderId;
            String side;
            // Execute the appropriate trade
            switch (signalEvent.getSignal()) {
                case LONG -> {
                    exchangeService.enterLongPosition(signalEvent.getSymbol(), quantity);
                    orderId = "LONG_" + System.currentTimeMillis(); // Simplified - would get from exchange
                    side = "BUY";
                }
                case SHORT -> {
                    exchangeService.enterShortPosition(signalEvent.getSymbol(), quantity);
                    orderId = "SHORT_" + System.currentTimeMillis(); // Simplified - would get from exchange
                    side = "SELL";
                }
                default -> throw new IllegalArgumentException("Invalid signal direction: " + signalEvent.getSignal());
            }

            // Place stop-loss and take-profit orders if provided
            if (signalEvent.getStopLoss() != null) {
                exchangeService.placeStopLossOrder(
                    signalEvent.getSymbol(),
                    side,
                    quantity,
                    signalEvent.getStopLoss()
                );
            }
            if (signalEvent.getTakeProfit() != null) {
                exchangeService.placeTakeProfitOrder(
                    signalEvent.getSymbol(),
                    side,
                    quantity,
                    signalEvent.getTakeProfit()
                );
            }

            // Get current price for execution record
            double executionPrice = exchangeService.getCurrentPrice(signalEvent.getSymbol());

            // Create execution event
            TradeExecutionEvent executionEvent = new TradeExecutionEvent(
                signalEvent.getBotId(), orderId, signalEvent.getSymbol()
            );
            executionEvent.setSide(side);
            executionEvent.setQuantity(quantity);
            executionEvent.setPrice(executionPrice);
            executionEvent.setStatus("FILLED"); // Simplified - would track actual status
            executionEvent.setTradeId("TRADE_" + System.currentTimeMillis());

            return executionEvent;

        } catch (Exception ex) {
            log.error("Trade execution failed for signal: {}", signalEvent.getEventId(), ex);
            throw new TradeExecutionException("Failed to execute trade", ex);
        }
    }
    
    /**
     * Calculates the appropriate position size for a trade signal.
     * 
     * @param signalEvent The trade signal
     * @param riskAssessment The risk assessment result
     * @return The calculated position size
     */
    private double calculatePositionSize(TradeSignalEvent signalEvent, RiskAssessment riskAssessment) {
        try {
            double balance = exchangeService.getMarginBalance();
            double price = exchangeService.getCurrentPrice(signalEvent.getSymbol());
            
            // Simple position sizing: use signal strength and risk assessment
            double riskPercentage = Math.min(riskAssessment.getMaxRiskPercentage(), 0.02); // Max 2%
            double signalStrength = Math.clamp(signalEvent.getStrength(), 0.1, 1.0); // Clamp to 0.1-1.0
            
            double riskAmount = balance * riskPercentage * signalStrength;
            double quantity = riskAmount / price;
            
            log.debug("Position size calculation: balance={}, price={}, risk%={}, strength={}, quantity={}", 
                balance, price, riskPercentage, signalStrength, quantity);
            
            return quantity;
            
        } catch (Exception ex) {
            log.error("Failed to calculate position size", ex);
            return 0.0;
        }
    }
    
    /**
     * Closes a position in response to a risk event.
     * 
     * @param riskEvent The risk event triggering the position close
     */
    private void closePosition(RiskEvent riskEvent) {
        try {
            log.info("Closing position for {} due to risk: {}", riskEvent.getSymbol(), riskEvent.getRiskType());
            
            // This would need to determine current position direction and size
            // For demonstration, we'll assume it's available from the risk event or position tracking
            exchangeService.exitLongPosition(riskEvent.getSymbol(), 0.01); // Simplified
            
            // Publish trade execution event
            TradeExecutionEvent closeEvent = new TradeExecutionEvent(
                riskEvent.getBotId(), "CLOSE_" + System.currentTimeMillis(), riskEvent.getSymbol()
            );
            closeEvent.setSide("CLOSE");
            closeEvent.setPrice(riskEvent.getCurrentPrice());
            closeEvent.setStatus("FILLED");
            
            eventPublisher.publishTradeExecution(closeEvent);
            
        } catch (Exception ex) {
            log.error("Failed to close position for risk event: {}", riskEvent.getEventId(), ex);
        }
    }
    
    /**
     * Reduces leverage in response to a risk event.
     * 
     * @param riskEvent The risk event triggering leverage reduction
     */
    private void reduceLeverage(RiskEvent riskEvent) {
        try {
            log.info("Reducing leverage for {} due to risk: {}", riskEvent.getSymbol(), riskEvent.getRiskType());
            
            // Reduce leverage to a safer level
            int newLeverage = Math.max(1, (int) (10 * 0.5)); // Reduce by 50%, minimum 1x
            exchangeService.setLeverage(riskEvent.getSymbol(), newLeverage);
            
            log.info("Leverage reduced to {}x for {}", newLeverage, riskEvent.getSymbol());
            
        } catch (Exception ex) {
            log.error("Failed to reduce leverage for risk event: {}", riskEvent.getEventId(), ex);
        }
    }
    
    /**
     * Publishes a risk event when a trade is rejected due to risk assessment.
     * 
     * @param signalEvent The original signal
     * @param riskAssessment The risk assessment that rejected the trade
     */
    private void publishRiskRejection(TradeSignalEvent signalEvent, RiskAssessment riskAssessment) {
        RiskEvent riskEvent = new RiskEvent(signalEvent.getBotId(), "TRADE_REJECTED", signalEvent.getSymbol());
        riskEvent.setSeverity("MEDIUM");
        riskEvent.setAction("ALERT_ONLY");
        riskEvent.setDescription("Trade signal rejected: " + riskAssessment.getRejectionReason());
        
        eventPublisher.publishRiskEvent(riskEvent);
        
        log.info("Trade signal rejected for bot: {} - Reason: {}", 
            signalEvent.getBotId(), riskAssessment.getRejectionReason());
    }
    
    /**
     * Handles trade execution errors by publishing appropriate events.
     * 
     * @param signalEvent The original signal that failed
     * @param exception The exception that occurred
     */
    private void handleTradeExecutionError(TradeSignalEvent signalEvent, Exception exception) {
        RiskEvent errorEvent = new RiskEvent(signalEvent.getBotId(), "EXECUTION_ERROR", signalEvent.getSymbol());
        errorEvent.setSeverity("HIGH");
        errorEvent.setAction("ALERT_ONLY");
        errorEvent.setDescription("Trade execution failed: " + exception.getMessage());
        
        eventPublisher.publishRiskEvent(errorEvent);
    }
    
    /**
     * Handles bot stopped events - may need to close open positions.
     * 
     * @param statusEvent The bot status event
     */
    private void handleBotStopped(tradingbot.bot.events.BotStatusEvent statusEvent) {
        log.info("Bot {} stopped - checking for open positions", statusEvent.getBotId());
        
        // If bot has an active position, consider closing it
        if (statusEvent.getActivePosition() != null && !statusEvent.getActivePosition().equals("NONE")) {
            log.warn("Bot {} stopped with active {} position - consider manual intervention", 
                statusEvent.getBotId(), statusEvent.getActivePosition());
            
            // Publish risk event for monitoring
            RiskEvent riskEvent = new RiskEvent(statusEvent.getBotId(), "BOT_STOPPED_WITH_POSITION", "ALL");
            riskEvent.setSeverity("HIGH");
            riskEvent.setAction("ALERT_ONLY");
            riskEvent.setDescription("Bot stopped with active " + statusEvent.getActivePosition() + " position");
            riskEvent.setCurrentPrice(statusEvent.getEntryPrice());
            
            eventPublisher.publishRiskEvent(riskEvent);
        }
    }
    
    /**
     * Handles bot error events - may need to stop all trading activity.
     * 
     * @param statusEvent The bot status event
     */
    private void handleBotError(tradingbot.bot.events.BotStatusEvent statusEvent) {
        log.error("Bot {} encountered error: {}", statusEvent.getBotId(), statusEvent.getMessage());
        
        // Publish high-severity risk event
        RiskEvent riskEvent = new RiskEvent(statusEvent.getBotId(), "BOT_ERROR", "ALL");
        riskEvent.setSeverity("CRITICAL");
        riskEvent.setAction("ALERT_ONLY");
        riskEvent.setDescription("Bot error: " + statusEvent.getMessage());
        
        eventPublisher.publishRiskEvent(riskEvent);
    }
    
    /**
     * Handles bot started events - log for monitoring.
     * 
     * @param statusEvent The bot status event
     */
    private void handleBotStarted(tradingbot.bot.events.BotStatusEvent statusEvent) {
        log.info("Bot {} started - ready for trade signals", statusEvent.getBotId());
        
        // Log current bot state for monitoring
        log.info("Bot {} state - Balance: ${}, Configuration: {}", 
            statusEvent.getBotId(), statusEvent.getCurrentBalance(), statusEvent.getConfigurationHash());
    }
    
    /**
     * Handles bot configuration changes - may affect trading parameters.
     * 
     * @param statusEvent The bot status event
     */
    private void handleBotConfigured(tradingbot.bot.events.BotStatusEvent statusEvent) {
        log.info("Bot {} configuration updated - Hash: {}", 
            statusEvent.getBotId(), statusEvent.getConfigurationHash());
        
        // Configuration changes might affect risk parameters or trading logic
        // This is where you might reload configuration or adjust parameters
        log.debug("Bot {} reconfigured with message: {}", statusEvent.getBotId(), statusEvent.getMessage());
    }
    
    /**
     * Custom exception for trade execution failures.
     */
    public static class TradeExecutionException extends RuntimeException {
        public TradeExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Risk assessment result class.
     */
    public static class RiskAssessment {
        private final boolean approved;
        private final String rejectionReason;
        private final double maxRiskPercentage;
        
        public RiskAssessment(boolean approved, String rejectionReason, double maxRiskPercentage) {
            this.approved = approved;
            this.rejectionReason = rejectionReason;
            this.maxRiskPercentage = maxRiskPercentage;
        }
        
        public boolean isApproved() { return approved; }
        public String getRejectionReason() { return rejectionReason; }
        public double getMaxRiskPercentage() { return maxRiskPercentage; }
    }
}