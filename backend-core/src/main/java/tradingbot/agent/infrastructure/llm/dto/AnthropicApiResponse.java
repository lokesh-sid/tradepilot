package tradingbot.agent.infrastructure.llm.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AnthropicApiResponse - Response DTO for Anthropic Messages API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicApiResponse(
        @JsonProperty("content") List<ContentBlock> content,
        @JsonProperty("usage") Usage usage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(
            @JsonProperty("type") String type,
            @JsonProperty("text") String text
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens
    ) {}

    public String extractText() {
        if (content == null || content.isEmpty()) return null;
        return content.stream()
                .filter(b -> "text".equals(b.type()))
                .map(ContentBlock::text)
                .findFirst()
                .orElse(null);
    }
}
