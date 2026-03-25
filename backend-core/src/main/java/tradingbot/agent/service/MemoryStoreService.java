package tradingbot.agent.service;

import java.util.List;

import tradingbot.agent.domain.model.TradeMemory;

/**
 * MemoryStoreService - Vector database interface for storing and retrieving trading experiences
 * 
 * This service manages the storage and semantic search of historical trading experiences
 * in a vector database (Pinecone, Weaviate, or Qdrant).
 */
public interface MemoryStoreService {
    
    /**
     * Store a trading experience in the vector database
     * 
     * @param experience The trading experience to store (must include embedding vector)
     */
    void store(TradeMemory experience);
    
    /**
     * Find similar trading experiences for a given scenario
     * 
     * @param queryEmbedding The embedding vector of the current scenario
     * @param symbol The trading symbol to filter by (e.g., "BTCUSDT")
     * @param topK Number of most similar experiences to retrieve
     * @return List of similar experiences, sorted by similarity score (highest first)
     */
    List<TradeMemory> findSimilar(double[] queryEmbedding, String symbol, int topK);
    
    /**
     * Find similar trading experiences with additional filters
     * 
     * @param queryEmbedding The embedding vector of the current scenario
     * @param symbol The trading symbol to filter by
     * @param topK Number of most similar experiences to retrieve
     * @param minSimilarity Minimum similarity threshold (0-1)
     * @param maxAgeDays Only include experiences from last N days (0 = no limit)
     * @return List of similar experiences, sorted by similarity score (highest first)
     */
    List<TradeMemory> findSimilar(
        double[] queryEmbedding, 
        String symbol, 
        int topK,
        double minSimilarity,
        int maxAgeDays
    );
    
    /**
     * Delete a trading experience from the vector database
     * 
     * @param experienceId The ID of the experience to delete
     */
    void delete(String experienceId);
    
    /**
     * Check if the vector database is healthy and accessible
     * 
     * @return true if the database is accessible, false otherwise
     */
    boolean isHealthy();
}
