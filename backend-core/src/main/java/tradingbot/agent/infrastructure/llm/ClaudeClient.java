package tradingbot.agent.infrastructure.llm;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.ReasoningContext;
import tradingbot.agent.infrastructure.llm.dto.AnthropicApiRequest;
import tradingbot.agent.infrastructure.llm.dto.AnthropicApiResponse;

/**
 * ClaudeClient - Integration with Anthropic Claude LLM via Messages API.
 */
@Component
@ConditionalOnProperty(name = "agent.llm.claude.enabled", havingValue = "true", matchIfMissing = false)
public class ClaudeClient implements LLMProvider {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeClient.class);

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final boolean enabled;
    private final RestTemplate restTemplate;

    public ClaudeClient(
            @Value("${agent.llm.claude.api-key}") String apiKey,
            @Value("${agent.llm.claude.model:claude-sonnet-4-6}") String model,
            @Value("${agent.llm.claude.temperature:0.7}") double temperature,
            @Value("${agent.llm.claude.max-tokens:2000}") int maxTokens,
            @Value("${agent.llm.claude.enabled}") boolean enabled) {
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.enabled = enabled;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public Reasoning generateReasoning(ReasoningContext context) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            logger.warn("Claude LLM is not properly configured. Returning fallback reasoning.");
            return createFallbackReasoning(context);
        }

        try {
            logger.info("Calling Claude LLM for agent reasoning");

            AnthropicApiRequest request = new AnthropicApiRequest(
                    model,
                    maxTokens,
                    PromptTemplates.getSystemPrompt(),
                    List.of(new AnthropicApiRequest.Message("user", PromptTemplates.buildReasoningPrompt(context))),
                    temperature
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", ANTHROPIC_VERSION);
            headers.set("Content-Type", "application/json");

            HttpEntity<AnthropicApiRequest> requestEntity = new HttpEntity<>(request, headers);

            ResponseEntity<AnthropicApiResponse> response = restTemplate.exchange(
                    API_URL,
                    HttpMethod.POST,
                    requestEntity,
                    AnthropicApiResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AnthropicApiResponse body = response.getBody();
                String text = body.extractText();
                if (text == null) {
                    logger.warn("Claude response contained no text content");
                    return createFallbackReasoning(context);
                }
                if (body.usage() != null) {
                    logger.debug("Claude API usage — input: {} tokens, output: {} tokens",
                            body.usage().inputTokens(), body.usage().outputTokens());
                }
                return ReasoningParser.parse(text, context);
            } else {
                logger.error("Claude API returned non-success status: {}", response.getStatusCode());
                return createFallbackReasoning(context);
            }

        } catch (Exception e) {
            logger.error("Error calling Claude LLM: {}", e.getMessage(), e);
            return createFallbackReasoning(context);
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getProviderName() {
        return "Claude (Anthropic)";
    }

    private Reasoning createFallbackReasoning(ReasoningContext context) {
        return new Reasoning(
                "Market data observed: " + context.getTradingSymbol() + " at $" + context.getPerception().getCurrentPrice(),
                "LLM unavailable - using conservative fallback strategy",
                "High risk: No LLM analysis available",
                "HOLD",
                0,
                Instant.now()
        );
    }
}
