package tradingbot.agent.infrastructure.llm.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AnthropicApiRequest - Request DTO for Anthropic Messages API.
 *
 * API format: POST https://api.anthropic.com/v1/messages
 */
public record AnthropicApiRequest(
        @JsonProperty("model") String model,
        @JsonProperty("max_tokens") int maxTokens,
        @JsonProperty("system") String system,
        @JsonProperty("messages") List<Message> messages,
        @JsonProperty("temperature") double temperature
) {
    public record Message(
            @JsonProperty("role") String role,
            @JsonProperty("content") String content
    ) {}
}
