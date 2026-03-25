package tradingbot.bot.controller;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tradingbot.bot.TradeDirection;
import tradingbot.bot.events.BotStatusEvent;
import tradingbot.bot.events.MarketDataEvent;
import tradingbot.bot.events.RiskEvent;
import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.events.TradeSignalEvent;
import tradingbot.bot.messaging.EventPublisher;

/**
 * Demo controller to showcase the simplified event publishing architecture.
 * 
 */
@RestController
@RequestMapping("/api/demo/events")
public class EventDemoController {
    
    private static final String DEMO_BOT = "demo-bot";
    private final EventPublisher eventPublisher;
    
    public EventDemoController(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Demo endpoint: Publish a trade signal event
     * GET /api/demo/events/signal?symbol=BTCUSDT&signal=BUY&strength=0.8
     */
    @GetMapping("/signal")
    public ResponseEntity<Map<String, Object>> publishTradeSignal(
            @RequestParam String symbol,
            @RequestParam TradeDirection signal,
            @RequestParam(defaultValue = "0.5") double strength) {
        
        TradeSignalEvent event = new TradeSignalEvent(DEMO_BOT, symbol, signal);
        event.setStrength(strength);
        
        eventPublisher.publishTradeSignal(event);
        
        return ResponseEntity.ok(Map.of(
            "message", "Trade signal published",
            "eventId", event.getEventId(),
            "symbol", symbol,
            "signal", signal,
            "strength", strength,
            "architecture", "Simplified Kafka-only approach - no Redis Streams complexity!"
        ));
    }
    
    /**
     * Demo endpoint: Publish a trade execution event  
     * GET /api/demo/events/execution?symbol=BTCUSDT&side=BUY&quantity=0.1&price=45000
     */
    @GetMapping("/execution")
    public ResponseEntity<Map<String, Object>> publishTradeExecution(
            @RequestParam String symbol,
            @RequestParam String side,
            @RequestParam double quantity,
            @RequestParam double price) {
        
        TradeExecutionEvent event = new TradeExecutionEvent(DEMO_BOT, "order-123", symbol);
        event.setSide(side);
        event.setQuantity(quantity);
        event.setPrice(price);
        event.setStatus("FILLED");
        
        CompletableFuture<Void> result = eventPublisher.publishTradeExecution(event);
        
        return ResponseEntity.ok(Map.of(
            "message", "Trade execution published",
            "eventId", event.getEventId(),
            "symbol", symbol,
            "side", side,
            "quantity", quantity,
            "price", price,
            "insight", "Single topic publishing - much simpler than dual Kafka/Redis!"
        ));
    }
    
    /**
     * Demo endpoint: Publish a risk event
     * GET /api/demo/events/risk?symbol=BTCUSDT&type=TRAILING_STOP_TRIGGERED&severity=HIGH
     */
    @GetMapping("/risk")
    public ResponseEntity<Map<String, Object>> publishRiskEvent(
            @RequestParam String symbol,
            @RequestParam String type,
            @RequestParam(defaultValue = "MEDIUM") String severity) {
        
        RiskEvent event = new RiskEvent(DEMO_BOT, type, symbol);
        event.setSeverity(severity);
        event.setDescription("Demo risk event - simplified architecture in action");
        event.setCurrentPrice(45000.0);
        
        eventPublisher.publishRiskEvent(event);
        
        return ResponseEntity.ok(Map.of(
            "message", "Risk event published",
            "eventId", event.getEventId(),
            "symbol", symbol,
            "riskType", type,
            "severity", severity,
            "improvement", "No more Redis Streams - unified Kafka approach!"
        ));
    }
    
    /**
     * Demo endpoint: Publish market data event
     * GET /api/demo/events/market?symbol=BTCUSDT&price=45000&volume=1000
     */
    @GetMapping("/market")
    public ResponseEntity<Map<String, Object>> publishMarketData(
            @RequestParam String symbol,
            @RequestParam double price,
            @RequestParam(defaultValue = "100") double volume) {
        
        MarketDataEvent event = new MarketDataEvent(DEMO_BOT, symbol, price);
        event.setVolume(volume);
        
        eventPublisher.publishMarketData(event);
        
        return ResponseEntity.ok(Map.of(
            "message", "Market data published",
            "eventId", event.getEventId(),
            "symbol", symbol,
            "price", price,
            "volume", volume,
            "simplification", "Single publishToTopic method handles all event types!"
        ));
    }
    
    /**
     * Demo endpoint: Publish bot status event
     * GET /api/demo/events/status?status=TRADING&message=Bot running smoothly
     */
    @GetMapping("/status")  
    public ResponseEntity<Map<String, Object>> publishBotStatus(
            @RequestParam String status,
            @RequestParam(defaultValue = "Status update") String message) {
        
        BotStatusEvent event = new BotStatusEvent(DEMO_BOT, status);
        event.setMessage(message);
        
        eventPublisher.publishBotStatus(event);
        
        return ResponseEntity.ok(Map.of(
            "message", "Bot status published",
            "eventId", event.getEventId(),
            "status", status,
            "statusMessage", message,
            "architecture_win", "Removed dual publishing complexity - much cleaner code!"
        ));
    }
    
    /**
     * Demo endpoint: Check event publisher health
     * GET /api/demo/events/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        boolean healthy = eventPublisher.isHealthy();
        
        return ResponseEntity.ok(Map.of(
            "healthy", healthy,
            "publisher", "EventPublisher (Simplified Architecture)",
            "topics", Map.of(
                "trade-signals", eventPublisher.getTopicEventCount("trade-signals"),
                "trade-execution", eventPublisher.getTopicEventCount("trade-execution"),
                "risk-events", eventPublisher.getTopicEventCount("risk-events"),
                "market-data", eventPublisher.getTopicEventCount("market-data"),
                "bot-status", eventPublisher.getTopicEventCount("bot-status")
            ),
            "architectural_improvement", "Single technology approach - Kafka alone handles all use cases!"
        ));
    }
}