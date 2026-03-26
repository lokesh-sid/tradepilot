package tradingbot.agent.infrastructure.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ChatMessageConverter implements AttributeConverter<ChatMessage, String> {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageConverter.class);

    @Override
    public String convertToDatabaseColumn(ChatMessage attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return ChatMessageSerializer.messageToJson(attribute);
        } catch (Exception e) {
            logger.error("Failed to serialize ChatMessage: {}", e.getMessage());
            throw new RuntimeException("Failed to serialize ChatMessage", e);
        }
    }

    @Override
    public ChatMessage convertToEntityAttribute(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        try {
            return ChatMessageDeserializer.messageFromJson(message);
        } catch (Exception e) {
            logger.error("Failed to deserialize ChatMessage: {}", e.getMessage());
            throw new RuntimeException("Failed to deserialize ChatMessage", e);
        }
    }
}
