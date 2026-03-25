package tradingbot.agent.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonProperty;

import tradingbot.bot.controller.exception.BotOperationException;

/**
 * OpenAIEmbeddingService - OpenAI implementation of EmbeddingService
 * 
 * Uses OpenAI's text-embedding-3-small model to generate 1536-dimensional
 * vector embeddings for semantic similarity search.
 */
@Service
@ConditionalOnProperty(name = "rag.embedding.provider", havingValue = "openai", matchIfMissing = true)
public class OpenAIEmbeddingService implements EmbeddingService {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenAIEmbeddingService.class);
    private static final String OPENAI_EMBEDDING_URL = "https://api.openai.com/v1/embeddings";
    private static final String MODEL = "text-embedding-3-small";
    private static final int DIMENSIONS = 1536;
    
    private final String apiKey;
    private final RestTemplate restTemplate;
    
    public OpenAIEmbeddingService(
            @Value("${openai.api.key:dummy}") String apiKey,
            RestTemplate restTemplate) {
        this.apiKey = apiKey;
        this.restTemplate = restTemplate;
    }
    
    @Override
    public double[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text to embed cannot be null or empty");
        }
        
        try {
            logger.debug("Generating embedding for text: {}", text.substring(0, Math.min(50, text.length())));
            
            var request = new EmbeddingRequest(text, MODEL);
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            var httpEntity = new HttpEntity<>(request, headers);
            var response = restTemplate.postForObject(
                OPENAI_EMBEDDING_URL,
                httpEntity,
                EmbeddingResponse.class
            );
            
            if (response == null || response.data() == null || response.data().isEmpty()) {
                throw new BotOperationException("generate_embedding", "OpenAI API returned empty response");
            }
            
            var embedding = response.data().get(0).embedding();
            logger.debug("Generated embedding with {} dimensions", embedding.length);
            
            return embedding;
            
        } catch (Exception e) {
            logger.error("Failed to generate embedding", e);
            throw new BotOperationException("generate_embedding", "Failed to generate embedding: " + e.getMessage(), e);
        }
    }
    
    @Override
    public int getDimensions() {
        return DIMENSIONS;
    }
    
    /**
     * Request DTO for OpenAI Embeddings API
     */
    private record EmbeddingRequest(
        String input,
        String model
    ) {}
    
    /**
     * Response DTO for OpenAI Embeddings API
     */
    private record EmbeddingResponse(
        String object,
        List<EmbeddingData> data,
        String model,
        Usage usage
    ) {}
    
    /**
     * Embedding data within the response
     */
    private record EmbeddingData(
        String object,
        double[] embedding,
        int index
    ) {}
    
    /**
     * Token usage information
     */
    private record Usage(
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("total_tokens") int totalTokens
    ) {}
}
