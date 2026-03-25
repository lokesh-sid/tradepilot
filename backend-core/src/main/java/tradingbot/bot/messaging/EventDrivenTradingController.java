package tradingbot.bot.messaging;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import tradingbot.bot.TradeDirection;
import tradingbot.bot.events.BotStatusEvent;
import tradingbot.bot.events.RiskEvent;
import tradingbot.bot.events.TradeSignalEvent;

/**
 * Demonstration controller showing how to integrate event-driven patterns
 * with the existing trading bot REST API. This shows the migration path
 * from synchronous to asynchronous, event-driven architecture.
 *
 * <p>Note: Event publishing is asynchronous. The API response only confirms
 * that the event was accepted for publishing, not that downstream processing
 * has completed successfully.</p>
 */
@RestController
@RequestMapping("/api/events")
@Tag(name = "Event-Driven Trading Demo", description = "Demonstration of event-driven trading patterns")
public class EventDrivenTradingController {
    
    private static final Logger log = LoggerFactory.getLogger(EventDrivenTradingController.class);
    
    // Response field constants
    private static final String EVENT_ID = "eventId";
    private static final String STATUS = "status";
    private static final String MESSAGE = "message";
    private static final String ERROR = "error";
    private static final String TIMESTAMP = "timestamp";
    private static final String PUBLISHED = "published";
    
    private final EventPublisher eventPublisher;
    private final TradeExecutionService tradeExecutionService;
    
    public EventDrivenTradingController(EventPublisher eventPublisher,
                                      TradeExecutionService tradeExecutionService) {
        this.eventPublisher = eventPublisher;
        this.tradeExecutionService = tradeExecutionService;
    }
    
    /**
     * Utility method to determine if the bot is considered running based on status string.
     */
    private boolean isBotRunning(String status) {
        return "RUNNING".equalsIgnoreCase(status) || "STARTED".equalsIgnoreCase(status);
    }
    
    @PostMapping("/trade-signal")
    @Operation(summary = "Publish a trade signal event", 
               description = "Demonstrates event-driven trade signal processing")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Signal event published successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid signal parameters"),
        @ApiResponse(responseCode = "500", description = "Event publishing failed")
    })
        public ResponseEntity<Map<String, Object>> publishTradeSignal(
            @Parameter(description = "Bot ID", required = true, example = "futures-bot-1")
            @RequestParam String botId,
            @Parameter(description = "Trading symbol", required = true, example = "BTCUSDT") 
            @RequestParam String symbol,
            @Parameter(description = "Signal direction", required = true, example = "LONG")
            @RequestParam TradeDirection direction,
            @Parameter(description = "Signal strength (0.0-1.0)", example = "0.75")
            @RequestParam(defaultValue = "0.5") double strength,
            @Parameter(description = "Stop-loss price (optional)", example = "42000.0")
            @RequestParam(required = false) Double stopLoss,
            @Parameter(description = "Take-profit price (optional)", example = "45000.0")
            @RequestParam(required = false) Double takeProfit) {
        
        try {
            // Create trade signal event with SL/TP
            TradeSignalEvent signalEvent = new TradeSignalEvent(botId, symbol, direction);
            signalEvent.setStrength(strength);
            signalEvent.setStopLoss(stopLoss);
            signalEvent.setTakeProfit(takeProfit);
            signalEvent.setMetadata(Map.of(
                "source", "manual",
                "confidence", "MEDIUM",
                "strategy", "demo_signal"
            ));

            // Publish the event asynchronously
            eventPublisher.publishTradeSignal(signalEvent)
                .exceptionally(ex -> {
                    log.error("Failed to publish trade signal: {}", signalEvent.getEventId(), ex);
                    return null;
                });

            // Process the signal (in production this would be handled by Kafka consumers)
            tradeExecutionService.handleTradeSignal(signalEvent);

            // Return immediately with event ID (async processing continues in background)
            Map<String, Object> tradeSignalResponse = Map.of(
                EVENT_ID, signalEvent.getEventId(),
                STATUS, PUBLISHED,
                MESSAGE, "Trade signal event published and processing started",
                TIMESTAMP, signalEvent.getOccurredAt()
            );

            return ResponseEntity.accepted().body(tradeSignalResponse);

        } catch (Exception ex) {
            log.error("Failed to publish trade signal", ex);
            return ResponseEntity.internalServerError().body(Map.of(
                ERROR, "Failed to publish trade signal",
                MESSAGE, ex.getMessage()
            ));
        }
    }
    
    @PostMapping("/risk-event")
    @Operation(summary = "Publish a risk event", 
               description = "Demonstrates risk event processing and position management")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Risk event published successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid risk parameters"),
        @ApiResponse(responseCode = "500", description = "Event publishing failed")
    })
    public ResponseEntity<Map<String, Object>> publishRiskEvent(
            @Parameter(description = "Bot ID", required = true, example = "futures-bot-1")
            @RequestParam String botId,
            @Parameter(description = "Trading symbol", required = true, example = "BTCUSDT")
            @RequestParam String symbol,
            @Parameter(description = "Risk type", required = true, example = "TRAILING_STOP_TRIGGERED")
            @RequestParam String riskType,
            @Parameter(description = "Current price", required = true, example = "43250.50")
            @RequestParam double currentPrice,
            @Parameter(description = "Risk severity", example = "HIGH")
            @RequestParam(defaultValue = "MEDIUM") String severity,
            @Parameter(description = "Recommended action", example = "CLOSE_POSITION")
            @RequestParam(defaultValue = "ALERT_ONLY") String action) {

        try {
            // Create risk event
            RiskEvent riskEvent = new RiskEvent(botId, riskType, symbol);
            riskEvent.setCurrentPrice(currentPrice);
            riskEvent.setSeverity(severity);
            riskEvent.setAction(action);
            riskEvent.setDescription("Manual risk event triggered via API");

            // Publish the event asynchronously
            eventPublisher.publishRiskEvent(riskEvent)
                        .exceptionally(ex -> {
                            log.error("Failed to publish risk event: {} (type={}, symbol={})", riskEvent.getEventId(), riskEvent.getRiskType(), riskEvent.getSymbol(), ex);
                            return null;
                        });

            // Process the risk event (in production this would be handled by Kafka consumers)
            tradeExecutionService.handleRiskEvent(riskEvent)
                        .exceptionally(ex -> {
                            log.error("Failed to process risk event: {} (type={}, symbol={})", riskEvent.getEventId(), riskEvent.getRiskType(), riskEvent.getSymbol(), ex);
                            return null;
                            });

            Map<String, Object> response = Map.of(
                EVENT_ID, riskEvent.getEventId(),
                STATUS, PUBLISHED,
                MESSAGE, "Risk event published and processing started",
                TIMESTAMP, riskEvent.getOccurredAt(),
                "severity", severity,
                "action", action
            );

            return ResponseEntity.accepted().body(response);

        } catch (Exception ex) {
            log.error("Failed to publish risk event", ex);
            return ResponseEntity.internalServerError().body(
                Map.of(
                    ERROR, "Failed to publish risk event",
                    MESSAGE, ex.getMessage()
                )
            );
        }
    }

    @PostMapping("/bot-status")
    @Operation(
        summary = "Publish a bot status event", 
        description = "Demonstrates bot status broadcasting for real-time monitoring. "
                    + "Note: Event publishing is asynchronous. The API response only confirms "
                    + "that the event was accepted for publishing, not that downstream processing "
                    + "has completed successfully."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Status event published successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status parameters"),
        @ApiResponse(responseCode = "500", description = "Event publishing failed")
    })
    public ResponseEntity<Map<String, Object>> publishBotStatusEvent(
            @Parameter(description = "Bot ID", required = true, example = "futures-bot-1")
            @RequestParam String botId,
            @Parameter(description = "Bot status", required = true, example = "RUNNING")
            @RequestParam String status,
            @RequestParam(required = false) String message,
            @Parameter(description = "Current balance", example = "1250.75")
            @RequestParam(defaultValue = "0.0") double balance,
            @Parameter(description = "Active position", example = "LONG")
            @RequestParam(defaultValue = "NONE") String activePosition) {
        
        try {
            // Create bot status event
            BotStatusEvent statusEvent = new BotStatusEvent(botId, status);
            statusEvent.setMessage(message != null ? message : "Status updated via API");
            statusEvent.setRunning(isBotRunning(status));
            statusEvent.setCurrentBalance(balance);
            statusEvent.setActivePosition(activePosition);
            
            // Publish the event to real-time stream
            eventPublisher.publishBotStatus(statusEvent)
                .exceptionally(ex -> {
                    log.error("Failed to publish bot status event: {}", statusEvent.getEventId(), ex);
                    return null;
                });

            tradeExecutionService.handleBotStatusEvent(statusEvent)
                .exceptionally(ex -> {
                    log.error("Failed to process bot status event: {}", statusEvent.getEventId(), ex);
                    return null;
                });

            Map<String, Object> response = Map.of(
                EVENT_ID, statusEvent.getEventId(),
                STATUS, PUBLISHED,
                MESSAGE, "Bot status event published to real-time stream",
                TIMESTAMP, statusEvent.getOccurredAt(),
                "botStatus", status
            );
            return ResponseEntity.accepted().body(response);
        } catch (Exception ex) {
            log.error("Failed to publish bot status event", ex);
            return ResponseEntity.internalServerError().body(Map.of(
                ERROR, "Failed to publish bot status event",
                MESSAGE, ex.getMessage()
            ));
        }
    }
    
    @GetMapping("/demo-info")
    @Operation(summary = "Get event-driven architecture demo information",
               description = "Provides information about the event-driven patterns and how to use them")
    public ResponseEntity<Map<String, Object>> getDemoInfo() {
        Map<String, Object> info = Map.of(
            "title", "Event-Driven Trading Bot Demo",
            "description", "This API demonstrates how to integrate message queues and event-driven patterns",
            "features", Map.of(
                "async_processing", "All events are processed asynchronously",
                "event_publishing", "Events are published to durable topics and real-time streams",
                "risk_management", "Risk events trigger automatic position management",
                "scalability", "Services can be scaled independently"
            ),
            "topics", Map.of(
                "trade-signals", "Durable topic for trade signals and analytics",
                "trade-execution", "Trade execution results and confirmations",
                "risk-events", "Risk management alerts and actions",
                "market-data", "Market price and volume updates"
            ),
            "streams", Map.of(
                "bot-status", "Real-time bot status updates",
                "trade-notifications", "Immediate trade confirmations",
                "system-alerts", "Real-time system alerts and warnings"
            ),
            "migration_path", Map.of(
                "phase1", "Add event publishing to existing services",
                "phase2", "Implement event consumers for async processing", 
                "phase3", "Replace direct service calls with event-driven communication",
                "phase4", "Add real-time features with WebSocket + Redis Streams",
                "phase5", "Implement full Kafka infrastructure for production"
            ),
            "next_steps", Map.of(
                "kafka_setup", "Set up Apache Kafka cluster for production",
                "consumer_groups", "Implement proper Kafka consumer groups",
                "monitoring", "Add Kafka monitoring and alerting",
                "testing", "Implement event-driven integration tests"
            )
        );
        
        return ResponseEntity.ok(info);
    }
}