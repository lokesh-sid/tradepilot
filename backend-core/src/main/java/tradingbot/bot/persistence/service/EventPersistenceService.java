package tradingbot.bot.persistence.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.events.TradeSignalEvent;
import tradingbot.bot.events.TradingEvent;
import tradingbot.bot.persistence.entity.TradeExecutionEventEntity;
import tradingbot.bot.persistence.entity.TradeSignalEventEntity;
import tradingbot.bot.persistence.entity.TradingEventEntity;
import tradingbot.bot.persistence.repository.TradingEventRepository;

/**
 * Service for persisting trading events to the database.
 * Converts domain events to JPA entities and stores them.
 */
@Service
@Transactional
public class EventPersistenceService {
    
    private static final Logger log = LoggerFactory.getLogger(EventPersistenceService.class);
    
    private final TradingEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    
    public EventPersistenceService(TradingEventRepository eventRepository, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Persists a trading event to the database.
     * 
     * @param event The trading event to persist
     */
    public void persistEvent(TradingEvent event) {
        try {
            TradingEventEntity entity = convertToEntity(event);
            eventRepository.save(entity);
            log.info("Persisted event: {} - {}", event.getEventType(), event.getEventId());
        } catch (Exception ex) {
            log.error("Failed to persist event: {} - {}", event.getEventType(), event.getEventId(), ex);
            throw new EventPersistenceException("Failed to persist event: " + event.getEventId(), ex);
        }
    }
    
    /**
     * Converts a domain event to a JPA entity.
     */
    private TradingEventEntity convertToEntity(TradingEvent event) {
        TradingEventEntity entity = switch (event) {
            case TradeSignalEvent signalEvent -> convertTradeSignalEvent(signalEvent);
            case TradeExecutionEvent executionEvent -> convertTradeExecutionEvent(executionEvent);
            default -> new GenericEventEntity();
        };
        
        // Set common fields
        entity.setEventId(event.getEventId());
        entity.setTimestamp(event.getOccurredAt());
        entity.setBotId(event.getBotId());
        entity.setEventType(event.getEventType());
        
        return entity;
    }
    
    private TradeSignalEventEntity convertTradeSignalEvent(TradeSignalEvent event) {
        TradeSignalEventEntity entity = new TradeSignalEventEntity();
        entity.setSymbol(event.getSymbol());
        entity.setSignalDirection(event.getSignal().name());
        entity.setConfidence(event.getStrength()); // Use strength as confidence
        
        // Extract current price from metadata if available
        if (event.getMetadata() != null && event.getMetadata().containsKey("currentPrice")) {
            Object priceObj = event.getMetadata().get("currentPrice");
            if (priceObj instanceof Number number) {
                entity.setCurrentPrice(number.doubleValue());
            }
        }
        
        // Convert indicators map to JSON string
        try {
            if (event.getIndicators() != null && !event.getIndicators().isEmpty()) {
                entity.setIndicators(objectMapper.writeValueAsString(event.getIndicators()));
            }
        } catch (Exception ex) {
            log.warn("Failed to serialize indicators for event: {}", event.getEventId(), ex);
        }
        
        return entity;
    }
    
    private TradeExecutionEventEntity convertTradeExecutionEvent(TradeExecutionEvent event) {
        TradeExecutionEventEntity entity = new TradeExecutionEventEntity();
        entity.setSymbol(event.getSymbol());
        entity.setOrderId(event.getOrderId());
        entity.setTradeId(event.getTradeId());
        entity.setSide(event.getSide());
        entity.setQuantity(event.getQuantity());
        entity.setPrice(event.getPrice());
        entity.setStatus(event.getStatus());
        // Profit/loss might not be available at execution time
        // Set to 0 for now - can be calculated later
        entity.setProfitLoss(0.0);
        
        return entity;
    }
    
    /**
     * Generic event entity for events that don't have specific entity types.
     */
    @Entity
    @DiscriminatorValue("GENERIC")
    private static class GenericEventEntity extends TradingEventEntity {
        // No additional fields needed
    }
    
    /**
     * Exception thrown when event persistence fails.
     */
    public static class EventPersistenceException extends RuntimeException {
        public EventPersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
