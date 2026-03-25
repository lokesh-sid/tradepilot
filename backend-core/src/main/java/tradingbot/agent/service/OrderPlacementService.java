package tradingbot.agent.service;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tradingbot.agent.application.OrderService;
import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.Order;
import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.TradeDirection;
import tradingbot.agent.infrastructure.persistence.OrderEntity;
import tradingbot.bot.controller.exception.BotOperationException;
import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.agent.config.AgentExecutionContext;
/**
 * OrderPlacementService - Parses LLM reasoning and executes orders
 * 
 * This service:
 * 1. Parses order details from LLM reasoning
 * 2. Validates confidence threshold
 * 3. Executes orders on Binance (or dry-run logs them)
 * 4. Publishes order events to Kafka
 * 5. Returns Order domain objects for tracking
 */
@Service
public class OrderPlacementService {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderPlacementService.class);
    
    // Regex patterns for parsing LLM output
    private static final Pattern DIRECTION_PATTERN = Pattern.compile(
        "(?i)(buy|long|sell|short|hold)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern QUANTITY_PATTERN = Pattern.compile(
        "(?i)quantity[:\\s]+(\\d+\\.?\\d*)|amount[:\\s]+(\\d+\\.?\\d*)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern STOP_LOSS_PATTERN = Pattern.compile(
        "(?i)stop[-\\s]?loss[:\\s]+(\\d+\\.?\\d*)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern TAKE_PROFIT_PATTERN = Pattern.compile(
        "(?i)take[-\\s]?profit[:\\s]+(\\d+\\.?\\d*)",
        Pattern.CASE_INSENSITIVE
    );
    
    private final EventPublisher eventPublisher;
    private final OrderService orderService;
    private final AgentExecutionContext executionContext;
    private final PositionMonitoringService positionMonitoringService;

    @Value("${rag.order.confidence-threshold:60}")
    private int confidenceThreshold;

    @Value("${rag.order.enabled:true}")
    private boolean orderExecutionEnabled;

    @Value("${rag.order.dry-run:true}")
    private boolean dryRun;

    @Value("${rag.order.max-position-size-percent:10}")
    private double maxPositionSizePercent;

    @Value("${rag.order.default-leverage:1}")
    private int defaultLeverage;

    public OrderPlacementService(
            EventPublisher eventPublisher,
            OrderService orderService,
            AgentExecutionContext executionContext,
            PositionMonitoringService positionMonitoringService) {
        this.eventPublisher = eventPublisher;
        this.orderService = orderService;
        this.executionContext = executionContext;
        this.positionMonitoringService = positionMonitoringService;
    }
    
    /**
     * Process reasoning and potentially place an order
     * 
     * @param agent The agent making the decision
     * @param perception Current market perception
     * @param reasoning LLM-generated reasoning
     * @return Order if one was placed, null if HOLD or confidence too low
     */
    public Order processReasoning(Agent agent, Perception perception, Reasoning reasoning) {
        
        logger.info("Processing reasoning for agent {} on {}: confidence={}%",
            agent.getId(), perception.getSymbol(), reasoning.getConfidence());
        
        // Check if order execution is enabled
        if (!orderExecutionEnabled) {
            logger.info("Order execution is disabled");
            return null;
        }
        
        // Check confidence threshold
        if (reasoning.getConfidence() < confidenceThreshold) {
            logger.info("Confidence {}% below threshold {}%, skipping order",
                reasoning.getConfidence(), confidenceThreshold);
            return null;
        }
        
        // Parse order details from reasoning
        OrderDetails details = parseOrderDetails(reasoning, perception);
        
        if (details == null || details.direction() == null) {
            logger.info("No clear trade direction found in reasoning, holding");
            return null;
        }
        
        // Choose fill price: ask for LONG entry (buyer pays), bid for SHORT entry (seller receives)
        double fillPrice = (details.direction() == TradeDirection.LONG)
                ? perception.getAskPrice()
                : perception.getBidPrice();

        // Validate position size
        double positionSize = details.quantity() * fillPrice;
        double maxPositionSize = agent.getCapital() * (maxPositionSizePercent / 100.0);
        
        if (positionSize > maxPositionSize) {
            logger.warn("Position size ${} exceeds maximum ${}, reducing quantity",
                String.format("%.2f", positionSize),
                String.format("%.2f", maxPositionSize));
            
            double adjustedQuantity = maxPositionSize / fillPrice;
            details = new OrderDetails(
                details.direction(),
                adjustedQuantity,
                details.stopLoss(),
                details.takeProfit()
            );
        }
        
        // Create order
        Order order = Order.builder()
            .id(UUID.randomUUID().toString())
            .agentId(agent.getId().toString())
            .symbol(perception.getSymbol())
            .direction(details.direction())
            .price(fillPrice)
            .quantity(details.quantity())
            .stopLoss(details.stopLoss())
            .takeProfit(details.takeProfit())
            .leverage(defaultLeverage)
            .status(Order.OrderStatus.PENDING)
            .createdAt(Instant.now())
            .build();
        
        // Execute or log order
        if (dryRun) {
            logDryRunOrder(order, reasoning);
        } else {
            executeOrder(order);
        }
        
        // Persist order to database
        persistOrder(order);
        
        // Publish order event
        publishOrderEvent(order, reasoning);
        
        return order;
    }
    
    /**
     * Parse order details from LLM reasoning
     */
    private OrderDetails parseOrderDetails(Reasoning reasoning, Perception perception) {
        String recommendation = reasoning.getRecommendation();
        
        // Parse direction
        TradeDirection direction = parseDirection(recommendation);
        if (direction == null) {
            return null;
        }
        
        // Parse quantity (default to small position if not specified)
        double quantity = parseQuantity(recommendation);
        if (quantity == 0) {
            // Default to 1% of available capital — use mid as reference for sizing
            quantity = (perception.getCurrentPrice() > 0) 
                ? 100.0 / perception.getCurrentPrice() 
                : 0.01;
        }
        
        // Parse risk management
        Double stopLoss = parseStopLoss(recommendation);
        Double takeProfit = parseTakeProfit(recommendation);
        
        return new OrderDetails(direction, quantity, stopLoss, takeProfit);
    }
    
    /**
     * Parse trade direction from text
     */
    private TradeDirection parseDirection(String text) {
        if (text == null) return null;
        
        Matcher matcher = DIRECTION_PATTERN.matcher(text);
        if (matcher.find()) {
            String direction = matcher.group(1).toLowerCase();
            if (direction.equals("buy") || direction.equals("long")) {
                return TradeDirection.LONG;
            } else if (direction.equals("sell") || direction.equals("short")) {
                return TradeDirection.SHORT;
            }
        }
        return null;
    }
    
    /**
     * Parse quantity from text
     */
    private double parseQuantity(String text) {
        if (text == null) return 0;
        
        Matcher matcher = QUANTITY_PATTERN.matcher(text);
        if (matcher.find()) {
            String quantityStr = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            try {
                return Double.parseDouble(quantityStr);
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse quantity: {}", quantityStr);
            }
        }
        return 0;
    }
    
    /**
     * Parse stop loss from text
     */
    private Double parseStopLoss(String text) {
        if (text == null) return null;
        
        Matcher matcher = STOP_LOSS_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse stop loss: {}", matcher.group(1));
            }
        }
        return null;
    }
    
    /**
     * Parse take profit from text
     */
    private Double parseTakeProfit(String text) {
        if (text == null) return null;
        
        Matcher matcher = TAKE_PROFIT_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse take profit: {}", matcher.group(1));
            }
        }
        return null;
    }
    
    /**
     * Log order in dry-run mode
     */
    private void logDryRunOrder(Order order, Reasoning reasoning) {
        logger.info("DRY RUN - Would place order: {} {} @ ${} (qty: {}, SL: {}, TP: {}) - Confidence: {}%",
            order.getDirection(),
            order.getSymbol(),
            String.format("%.2f", order.getPrice()),
            order.getQuantity(),
            order.getStopLoss() != null ? String.format("$%.2f", order.getStopLoss()) : "none",
            order.getTakeProfit() != null ? String.format("$%.2f", order.getTakeProfit()) : "none",
            reasoning.getConfidence()
        );
        
        logger.info("Reasoning: {}", reasoning.getRecommendation());
    }
    
        /**
     * Execute order on Exchange Futures (Bybit/Binance)
     * Integrates with exchange API to place real orders
     */
    private void executeOrder(Order order) {
        logger.info("LIVE - Placing order: {} {} @ ${} (qty: {}, leverage: {})",
            order.getDirection(),
            order.getSymbol(),
            String.format("%.2f", order.getPrice()),
            order.getQuantity(),
            order.getLeverage() != null ? order.getLeverage() : defaultLeverage
        );
        
        try {
            // Set leverage if specified
            int leverage = order.getLeverage() != null ? order.getLeverage() : defaultLeverage;
            if (leverage > 1) {
                executionContext.get().setLeverage(order.getSymbol(), leverage);
                logger.info("Set leverage to {}x for {}", leverage, order.getSymbol());
            }
            
            // Execute the main order based on direction
            tradingbot.bot.service.OrderResult orderResult;
            if (order.getDirection() == TradeDirection.LONG) {
                orderResult = executionContext.get().enterLongPosition(order.getSymbol(), order.getQuantity());
                logger.info("Entered LONG position: {} units of {} - Exchange Order ID: {}", 
                    order.getQuantity(), order.getSymbol(), orderResult.getExchangeOrderId());
            } else if (order.getDirection() == TradeDirection.SHORT) {
                orderResult = executionContext.get().enterShortPosition(order.getSymbol(), order.getQuantity());
                logger.info("Entered SHORT position: {} units of {} - Exchange Order ID: {}", 
                    order.getQuantity(), order.getSymbol(), orderResult.getExchangeOrderId());
            } else {
                logger.error("Unknown trade direction: {}", order.getDirection());
                order.markFailed("Unknown trade direction: " + order.getDirection());
                return;
            }
            
            // Mark order as executed with real exchange order ID
            order.markExecuted(orderResult.getExchangeOrderId(), Instant.now());
            logger.info("Order executed successfully: {} - Exchange Order ID: {}", 
                order.getId(), orderResult.getExchangeOrderId());
            
            // Create position record for tracking
            tradingbot.agent.domain.model.Position position = positionMonitoringService.createPosition(
                order.getAgentId(),
                order.getSymbol(),
                order.getDirection(),
                orderResult.getAvgFillPrice(), // Use actual fill price
                orderResult.getFilledQuantity(), // Use actual filled quantity
                order.getStopLoss(),
                order.getTakeProfit(),
                orderResult.getExchangeOrderId()
            );
            logger.info("Created position record: {}", position.getId());
            
            // Implement stop-loss and take-profit as separate orders
            if (order.getStopLoss() != null || order.getTakeProfit() != null) {
                placeStopLossAndTakeProfit(order, orderResult);
            }
            
        } catch (Exception e) {
            logger.error("Failed to execute order on exchange", e);
            order.markFailed(e.getMessage());
            throw new BotOperationException("execute_order", "Exchange order execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Place stop-loss and take-profit orders after main order fills
     * These are separate reduce-only orders that close the position
     */
    private void placeStopLossAndTakeProfit(Order order, tradingbot.bot.service.OrderResult mainOrderResult) {
        try {
            String symbol = order.getSymbol();
            double quantity = order.getQuantity();
            // Map direction to exchange action only here
            String closingAction = (order.getDirection() == TradeDirection.LONG) ? "SELL" : "BUY";

            // Place stop-loss order if specified
            if (order.getStopLoss() != null) {
                try {
                    tradingbot.bot.service.OrderResult slResult = executionContext.get().placeStopLossOrder(
                        symbol,
                        closingAction,
                        quantity,
                        order.getStopLoss()
                    );
                    logger.info("Placed stop-loss order for {} at ${} - Order ID: {}",
                        symbol, order.getStopLoss(), slResult.getExchangeOrderId());
                } catch (Exception e) {
                    logger.error("Failed to place stop-loss order: {}", e.getMessage());
                }
            }

            // Place take-profit order if specified
            if (order.getTakeProfit() != null) {
                try {
                    tradingbot.bot.service.OrderResult tpResult = executionContext.get().placeTakeProfitOrder(
                        symbol,
                        closingAction,
                        quantity,
                        order.getTakeProfit()
                    );
                    logger.info("Placed take-profit order for {} at ${} - Order ID: {}",
                        symbol, order.getTakeProfit(), tpResult.getExchangeOrderId());
                } catch (Exception e) {
                    logger.error("Failed to place take-profit order: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error in placeStopLossAndTakeProfit: {}", e.getMessage());
        }
    }
    
    /**
     * Persist order to database
     */
    private void persistOrder(Order order) {
        try {
            OrderEntity entity = new OrderEntity(
                order.getId(),
                order.getAgentId(),
                order.getSymbol(),
                OrderEntity.Direction.valueOf(order.getDirection().name()),
                order.getPrice(),
                order.getQuantity(),
                order.getStopLoss(),
                order.getTakeProfit(),
                order.getLeverage(),
                OrderEntity.Status.valueOf(order.getStatus().name()),
                order.getCreatedAt()
            );
            entity.setExecutedAt(order.getExecutedAt());
            entity.setExchangeOrderId(order.getExchangeOrderId());
            entity.setFailureReason(order.getFailureReason());
            orderService.createOrder(entity);
            logger.debug("Persisted order {}", order.getId());
        } catch (Exception e) {
            logger.error("Failed to persist order to database", e);
            // Don't throw - order execution should not fail due to persistence issues
        }
    }
    
    /**
     * Publish order event to Kafka
     */
    private void publishOrderEvent(Order order, Reasoning reasoning) {
        try {
            // Create trade execution event
            var event = new TradeExecutionEvent(
                order.getAgentId(), // botId
                order.getId(), // orderId
                order.getSymbol()
            );
            // Map direction to side only for event publishing
            event.setSide(order.getDirection() == TradeDirection.LONG ? "BUY" : "SELL");
            event.setPrice(order.getPrice());
            event.setQuantity(order.getQuantity());
            event.setStatus(order.getStatus().name());
            event.setTradeId(order.getExchangeOrderId());

            eventPublisher.publishTradeExecution(event);
            logger.debug("Published order event for {}", order.getId());
        } catch (Exception e) {
            logger.error("Failed to publish order event", e);
        }
    }
    
    /**
     * Internal record for parsed order details
     */
    private record OrderDetails(
        TradeDirection direction,
        double quantity,
        Double stopLoss,
        Double takeProfit
    ) {}
}
