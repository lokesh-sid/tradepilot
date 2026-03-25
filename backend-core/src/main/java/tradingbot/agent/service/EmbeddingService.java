package tradingbot.agent.service;

/**
 * EmbeddingService - Converts text into vector embeddings for semantic search
 * 
 * This service transforms unstructured text (like trade scenarios) into
 * numerical vectors that can be compared for semantic similarity.
 */
public interface EmbeddingService {
    
    /**
     * Generate an embedding vector for the given text
     * 
     * @param text The text to embed (e.g., "BTC trending up, RSI at 65, breaking resistance")
     * @return A vector representation (typically 1536 dimensions for OpenAI)
     */
    double[] embed(String text);
    
    /**
     * Calculate cosine similarity between two embedding vectors
     * 
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return Similarity score between 0 and 1 (1 = identical, 0 = completely different)
     */
    default double cosineSimilarity(double[] embedding1, double[] embedding2) {
        if (embedding1.length != embedding2.length) {
            throw new IllegalArgumentException(
                "Embeddings must have the same dimensions: " + 
                embedding1.length + " vs " + embedding2.length
            );
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * Get the dimension size of embeddings produced by this service
     * 
     * @return The number of dimensions (e.g., 1536 for OpenAI text-embedding-3-small)
     */
    int getDimensions();
}
