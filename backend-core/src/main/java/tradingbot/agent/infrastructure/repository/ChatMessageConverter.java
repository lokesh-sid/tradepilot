package tradingbot.agent.infrastructure.repository;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.*;
import static com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import dev.langchain4j.data.message.ChatMessage;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Component
@Converter
public class ChatMessageConverter implements AttributeConverter<ChatMessage, String> {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageConverter.class);
    private static volatile ObjectMapper persistenceMapper;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        // Restrict deserialization to ChatMessage and its subtypes in the expected package
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(ChatMessage.class)
            .allowIfSubType("dev.langchain4j.data.message.")
            .build();

        ObjectMapper mapper = objectMapper.copy();
        mapper.activateDefaultTyping(ptv, NON_FINAL, PROPERTY);
        // Atomic publish
        ChatMessageConverter.persistenceMapper = mapper;
    }

    @Override
    public String convertToDatabaseColumn(ChatMessage attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return persistenceMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
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
            return persistenceMapper.readValue(message, ChatMessage.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize ChatMessage: {}", e.getMessage());
            throw new RuntimeException("Failed to deserialize ChatMessage", e);
        }
    }
}
