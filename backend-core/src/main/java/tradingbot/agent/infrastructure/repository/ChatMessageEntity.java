package tradingbot.agent.infrastructure.repository;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * ChatMessageEntity - JPA Entity for persisting agent conversation history
 */
@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_chat_agent_id", columnList = "agent_id"),
    @Index(name = "idx_chat_timestamp", columnList = "timestamp")
})
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Convert(converter = ChatMessageConverter.class)
    @Column(name = "message_json", columnDefinition = "TEXT", nullable = false)
    private dev.langchain4j.data.message.ChatMessage message;

    @Column(nullable = false)
    private Instant timestamp;

    protected ChatMessageEntity() {}

    public ChatMessageEntity(String agentId, dev.langchain4j.data.message.ChatMessage message, Instant timestamp) {
        this.agentId = agentId;
        this.message = message;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public String getAgentId() { return agentId; }
    public dev.langchain4j.data.message.ChatMessage getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
}
