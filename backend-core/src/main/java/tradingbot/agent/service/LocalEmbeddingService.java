package tradingbot.agent.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * LocalEmbeddingService - Simple local implementation for testing/development
 * 
 * This is a mock implementation that generates deterministic embeddings
 * based on text hash. NOT suitable for production - use OpenAIEmbeddingService
 * or a proper sentence-transformers model instead.
 * 
 * Activated when: rag.embedding.provider=local
 */
@Service
@ConditionalOnProperty(name = "rag.embedding.provider", havingValue = "local")
public class LocalEmbeddingService implements EmbeddingService {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalEmbeddingService.class);
    private static final int DIMENSIONS = 384;  // Common size for sentence-transformers
    
    private final Map<String, double[]> cache = new HashMap<>();
    
    @Override
    public double[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text to embed cannot be null or empty");
        }
        
        // Check cache first
        if (cache.containsKey(text)) {
            logger.debug("Returning cached embedding for: {}", text.substring(0, Math.min(50, text.length())));
            return cache.get(text);
        }
        
        logger.debug("Generating local embedding for: {}", text.substring(0, Math.min(50, text.length())));
        
        // Generate deterministic embedding based on text hash
        double[] embedding = generateDeterministicEmbedding(text);
        cache.put(text, embedding);
        
        return embedding;
    }
    
    @Override
    public int getDimensions() {
        return DIMENSIONS;
    }
    
    /**
     * Generate a deterministic embedding vector based on text content
     * 
     * This uses the text's hashCode as a seed for reproducible results.
     * The embedding will be normalized to unit length for proper cosine similarity.
     */
    private double[] generateDeterministicEmbedding(String text) {
        double[] embedding = new double[DIMENSIONS];
        Random random = new Random(text.hashCode());
        
        // Generate random values
        for (int i = 0; i < DIMENSIONS; i++) {
            embedding[i] = random.nextGaussian();
        }
        
        // Normalize to unit length
        double norm = 0.0;
        for (double value : embedding) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        
        for (int i = 0; i < DIMENSIONS; i++) {
            embedding[i] /= norm;
        }
        
        return embedding;
    }
}
