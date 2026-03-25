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
 * GrokEmbeddingService - Grok-based implementation of EmbeddingService
 * 
 * Note: X.AI Grok doesn't have a native embeddings API yet.
 * This implementation uses a workaround:
 * 1. Uses Grok to generate semantic representations of text
 * 2. Converts the text into a fixed-dimension vector using hash-based approach
 * 
 * Alternative: Could use sentence transformers or other embedding models
 * that are compatible with Grok's use cases.
 * 
 * For production, consider:
 * - Using OpenAI embeddings (more mature)
 * - Using open-source models like sentence-transformers
 * - Waiting for X.AI to release native embeddings API
 */
@Service
@ConditionalOnProperty(name = "rag.embedding.provider", havingValue = "grok")
public class GrokEmbeddingService implements EmbeddingService {
    
    private static final Logger logger = LoggerFactory.getLogger(GrokEmbeddingService.class);
    private static final String GROK_API_URL = "https://api.x.ai/v1/chat/completions";
    private static final String MODEL = "grok-beta";
    
    // Using 1536 dimensions to match OpenAI for compatibility with existing Pinecone index
    private static final int DIMENSIONS = 1536;
    
    private final String apiKey;
    private final RestTemplate restTemplate;
    
    public GrokEmbeddingService(
            @Value("${agent.llm.grok.api-key}") String apiKey,
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
            logger.debug("Generating Grok-based embedding for text: {}", 
                text.substring(0, Math.min(50, text.length())));
            
            // Approach: Use Grok to generate a semantic summary, then convert to vector
            // This is a workaround until X.AI releases native embeddings
            String semanticSummary = generateSemanticSummary(text);
            double[] embedding = textToVector(semanticSummary + text);
            
            logger.debug("Generated Grok embedding with {} dimensions", embedding.length);
            return embedding;
            
        } catch (Exception e) {
            logger.error("Failed to generate Grok embedding", e);
            throw new BotOperationException("generate_embedding", "Failed to generate embedding: " + e.getMessage(), e);
        }
    }
    
    @Override
    public int getDimensions() {
        return DIMENSIONS;
    }
    
    /**
     * Generate a semantic summary using Grok to capture key concepts
     */
    private String generateSemanticSummary(String text) {
        try {
            var request = new GrokRequest(
                MODEL,
                List.of(
                    new Message("system", 
                        "Extract key semantic concepts from the text in a single line. " +
                        "Focus on: market conditions, technical indicators, sentiment, direction, price levels."),
                    new Message("user", "Text: " + text)
                ),
                0.3,  // Low temperature for consistency
                100   // Short response
            );
            
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            var httpEntity = new HttpEntity<>(request, headers);
            var response = restTemplate.postForObject(
                GROK_API_URL,
                httpEntity,
                GrokResponse.class
            );
            
            if (response != null && response.choices() != null && !response.choices().isEmpty()) {
                return response.choices().get(0).message().content();
            }
            
            return "";
            
        } catch (Exception e) {
            logger.warn("Failed to generate semantic summary with Grok, using direct text: {}", 
                e.getMessage());
            return "";
        }
    }
    
    /**
     * Convert text to a fixed-dimension vector using a deterministic approach
     * 
     * This uses a combination of:
     * 1. Character-level features
     * 2. Word-level features
     * 3. N-gram features
     * 4. Hash-based distribution
     * 
     * Note: This is a simplified embedding approach. For production, consider:
     * - Using sentence-transformers library
     * - Using OpenAI embeddings
     * - Waiting for X.AI native embeddings API
     */
    private double[] textToVector(String text) {
        double[] vector = new double[DIMENSIONS];
        
        // Normalize text
        String normalized = text.toLowerCase().trim();
        
        // 1. Character distribution (first 256 dimensions)
        int[] charCounts = new int[256];
        for (char c : normalized.toCharArray()) {
            if (c < 256) charCounts[c]++;
        }
        for (int i = 0; i < 256; i++) {
            vector[i] = charCounts[i] / (double) Math.max(1, normalized.length());
        }
        
        // 2. Word-level features (next 512 dimensions)
        String[] words = normalized.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            int hash = Math.abs(words[i].hashCode()) % 512;
            vector[256 + hash] += 1.0 / words.length;
        }
        
        // 3. Bigram features (next 512 dimensions)
        for (int i = 0; i < words.length - 1; i++) {
            String bigram = words[i] + " " + words[i + 1];
            int hash = Math.abs(bigram.hashCode()) % 512;
            vector[768 + hash] += 1.0 / Math.max(1, words.length - 1);
        }
        
        // 4. Trigram features (remaining 256 dimensions)
        for (int i = 0; i < words.length - 2; i++) {
            String trigram = words[i] + " " + words[i + 1] + " " + words[i + 2];
            int hash = Math.abs(trigram.hashCode()) % 256;
            vector[1280 + hash] += 1.0 / Math.max(1, words.length - 2);
        }
        
        // Normalize vector to unit length
        double magnitude = 0;
        for (double v : vector) {
            magnitude += v * v;
        }
        magnitude = Math.sqrt(magnitude);
        
        if (magnitude > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= magnitude;
            }
        }
        
        return vector;
    }
    
    // DTOs for Grok API
    private record GrokRequest(
        String model,
        List<Message> messages,
        double temperature,
        @JsonProperty("max_tokens") int maxTokens
    ) {}
    
    private record Message(
        String role,
        String content
    ) {}
    
    private record GrokResponse(
        List<Choice> choices
    ) {}
    
    private record Choice(
        Message message
    ) {}
}
