package tradingbot.bot.persistence.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * JPA Entity for storing trading events in the database.
 * Provides durable persistence layer for event sourcing and analytics.
 */
@Entity
@Table(name = "trading_events", indexes = {
    @Index(name = "idx_bot_id", columnList = "bot_id"),
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_symbol", columnList = "symbol")
})
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "event_type", discriminatorType = DiscriminatorType.STRING)
public abstract class TradingEventEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;
    
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;
    
    @Column(name = "bot_id", nullable = false)
    private String botId;
    
    @Column(name = "event_type", insertable = false, updatable = false)
    private String eventType;
    
    @Column(name = "symbol")
    private String symbol;
    
    @ElementCollection
    @CollectionTable(name = "event_metadata", joinColumns = @JoinColumn(name = "event_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", length = 1000)
    private Map<String, String> metadata = new HashMap<>();
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
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
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
