package tradingbot.bot.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Abstract base class for all event payloads.
 * Contains common event metadata fields that all events share.
 * 
 * @param <T> The type of the event-specific data
 */
public abstract class EventPayload<T> {
    
    @JsonProperty("eventId")
    private String eventId;
    
    @JsonProperty("eventType")
    private String eventType;
    
    @JsonProperty("data")
    private T data;
    
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", data=" + data +
                '}';
    }
}
