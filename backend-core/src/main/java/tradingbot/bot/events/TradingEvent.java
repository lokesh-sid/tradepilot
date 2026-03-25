package tradingbot.bot.events;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for all trading events in the system.
 * Provides common fields and behavior for event-driven architecture.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TradeSignalEvent.class, name = "TRADE_SIGNAL"),
    @JsonSubTypes.Type(value = TradeExecutionEvent.class, name = "TRADE_EXECUTED"),
    @JsonSubTypes.Type(value = RiskEvent.class, name = "RISK_ALERT"),
    @JsonSubTypes.Type(value = MarketDataEvent.class, name = "MARKET_DATA"),
    @JsonSubTypes.Type(value = BotStatusEvent.class, name = "BOT_STATUS")
})
public abstract class TradingEvent {
    
    private String eventId = UUID.randomUUID().toString();
    private Instant occurredAt = Instant.now();
    private String botId;
    private String eventType;
    
    protected TradingEvent() {}
    
    protected TradingEvent(String botId, String eventType) {
        this.botId = botId;
        this.eventType = eventType;
    }
    
    // Getters and Setters
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public Instant getOccurredAt() {
        return occurredAt;
    }
    
    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
    
    public String getBotId() {
        return botId;
    }
    
    public void setBotId(String botId) {
        this.botId = botId;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    @Override
    public String toString() {
        return "TradingEvent{" +
                "eventId='" + eventId + '\'' +
                ", occurredAt=" + occurredAt +
                ", botId='" + botId + '\'' +
                ", eventType='" + eventType + '\'' +
                '}';
    }
}