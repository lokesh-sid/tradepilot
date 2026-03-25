package tradingbot.bot.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import tradingbot.bot.events.BotStatusEvent;
import tradingbot.bot.events.MarketDataEvent;
import tradingbot.bot.events.RiskEvent;
import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.events.TradeSignalEvent;
import tradingbot.bot.messaging.EventPublisher.EventWrapper;
import tradingbot.bot.persistence.service.EventPersistenceService;

/**
 * Kafka event consumer for trading events.
 * 
 * This service demonstrates how to consume events from Kafka topics.
 * In production, you would have dedicated consumer services for each
 * event type with proper error handling and dead letter queues.
 */
@Service
public class EventConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);
    
    private final EventPersistenceService eventPersistenceService;
    
    public EventConsumer(EventPersistenceService eventPersistenceService) {
        this.eventPersistenceService = eventPersistenceService;
    }
    
    /**
     * Consumes trade signal events from Kafka.
     * 
     * @param payload The event payload
     * @param partition The Kafka partition
     * @param offset The message offset
     * @param key The partition key
     */
    @KafkaListener(topics = "trading.signals", groupId = "trading-bot-signals")
    public void handleTradeSignal(@Payload EventWrapper payload,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset,
                                  @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        try {
            log.info("Received trade signal event: key={}, partition={}, offset={}", key, partition, offset);
            log.debug("Trade signal payload: {}", payload);
            
            // Extract event data
            String eventId = payload.getEventId();
            String eventType = payload.getEventType();
            TradeSignalEvent event = (TradeSignalEvent) payload.getData();
            
            log.info("Processing {} event {} for symbol: {}, signal: {}, strength: {}", 
                eventType, eventId, event.getSymbol(), event.getSignal(), event.getStrength());
            
            // Persist the event
            eventPersistenceService.persistEvent(event);
            
        } catch (Exception ex) {
            log.error("Failed to process trade signal event from partition {} offset {}", partition, offset, ex);
            // In production, you would send to a dead letter queue
        }
    }
    
    /**
     * Consumes trade execution events from Kafka.
     */
    @KafkaListener(topics = "trading.executions", groupId = "trading-bot-executions")
    public void handleTradeExecution(@Payload EventWrapper payload,
                                     @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                     @Header(KafkaHeaders.OFFSET) long offset,
                                     @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        try {
            log.info("Received trade execution event: key={}, partition={}, offset={}", key, partition, offset);
            
            String eventId = payload.getEventId();
            String eventType = payload.getEventType();
            TradeExecutionEvent event = (TradeExecutionEvent) payload.getData();
            
            log.info("Processing {} event {} for symbol: {}, side: {}, quantity: {}", 
                eventType, eventId, event.getSymbol(), event.getSide(), event.getQuantity());
            
            // Persist the event
            eventPersistenceService.persistEvent(event);
            
        } catch (Exception ex) {
            log.error("Failed to process trade execution event from partition {} offset {}", partition, offset, ex);
        }
    }
    
    /**
     * Consumes risk events from Kafka.
     */
    @KafkaListener(topics = "trading.risk", groupId = "trading-bot-risk")
    public void handleRiskEvent(@Payload EventWrapper payload,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                @Header(KafkaHeaders.OFFSET) long offset,
                                @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        try {
            log.warn("Received risk event: key={}, partition={}, offset={}", key, partition, offset);
            
            String eventId = payload.getEventId();
            String eventType = payload.getEventType();
            RiskEvent event = (RiskEvent) payload.getData();
            
            log.warn("Processing {} risk event {} - type: {}, severity: {}, action: {}", 
                eventType, eventId, event.getRiskType(), event.getSeverity(), event.getAction());
            
            // Persist the event
            eventPersistenceService.persistEvent(event);
            
        } catch (Exception ex) {
            log.error("Failed to process risk event from partition {} offset {}", partition, offset, ex);
        }
    }
    
    /**
     * Consumes market data events from Kafka.
     */
    @KafkaListener(topics = "trading.market-data", groupId = "trading-bot-market-data")
    public void handleMarketData(@Payload EventWrapper payload,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset,
                                 @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        try {
            log.debug("Received market data event: key={}, partition={}, offset={}", key, partition, offset);
            
            // Market data events are high-frequency, so we use debug level
            String eventId = payload.getEventId();
            MarketDataEvent event = (MarketDataEvent) payload.getData();
            
            log.debug("Processing market data event {} for symbol: {}, price: {}", 
                eventId, event.getSymbol(), event.getPrice());
            
            // Persist the event
            eventPersistenceService.persistEvent(event);
            
        } catch (Exception ex) {
            log.error("Failed to process market data event from partition {} offset {}", partition, offset, ex);
        }
    }
    
    /**
     * Consumes bot status events from Kafka.
     */
    @KafkaListener(topics = "trading.bot-status", groupId = "trading-bot-status")
    public void handleBotStatus(@Payload EventWrapper payload,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                @Header(KafkaHeaders.OFFSET) long offset,
                                @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        try {
            log.info("Received bot status event: key={}, partition={}, offset={}", key, partition, offset);
            
            String eventId = payload.getEventId();
            String eventType = payload.getEventType();
            BotStatusEvent event = (BotStatusEvent) payload.getData();
            
            log.info("Processing {} status event {} - status: {}", 
                eventType, eventId, event.getStatus());
            
            // Persist the event
            eventPersistenceService.persistEvent(event);
            
        } catch (Exception ex) {
            log.error("Failed to process bot status event from partition {} offset {}", partition, offset, ex);
        }
    }
}