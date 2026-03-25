package tradingbot.agent.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonProperty;

import tradingbot.agent.domain.model.TradeDirection;
import tradingbot.agent.domain.model.TradeMemory;
import tradingbot.agent.domain.model.TradeOutcome;
import tradingbot.bot.controller.exception.BotOperationException;

/**
 * PineconeMemoryStore - Pinecone implementation of MemoryStoreService
 * 
 * Uses Pinecone vector database for high-performance semantic search
 * of trading memories. Supports metadata filtering and similarity thresholds.
 */
@Service
public class PineconeMemoryStore implements MemoryStoreService {
    
    private static final String TIMESTAMP = "timestamp";

    private static final String SYMBOL = "symbol";

    private static final String API_KEY = "Api-Key";

    private static final Logger logger = LoggerFactory.getLogger(PineconeMemoryStore.class);
    
    private final String apiKey;
    private final String environment;
    private final String indexName;
    private final String namespace;
    private final RestTemplate restTemplate;
    private final String baseUrl;
    
    public PineconeMemoryStore(
            @Value("${rag.vector-db.api-key:dummy}") String apiKey,
            @Value("${rag.vector-db.environment:dummy}") String environment,
            @Value("${rag.vector-db.index-name:dummy}") String indexName,
            @Value("${rag.vector-db.namespace:default}") String namespace,
            RestTemplate restTemplate) {
        this.apiKey = apiKey;
        this.environment = environment;
        this.indexName = indexName;
        this.namespace = namespace;
        this.restTemplate = restTemplate;
        this.baseUrl = String.format("https://%s-%s.svc.%s.pinecone.io",
            indexName, "project-id", environment); // TODO: Get project-id from config
        
        logger.info("Initialized PineconeMemoryStore with index: {}, namespace: {}", 
            indexName, namespace);
    }
    
    @Override
    public void store(TradeMemory memory) {
        if (memory.getEmbedding() == null || memory.getEmbedding().length == 0) {
            throw new IllegalArgumentException("Memory must have embedding vector to store");
        }
        
        try {
            logger.debug("Storing memory {} in Pinecone", memory.getId());
            
            // Build metadata for filtering
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("agentId", memory.getAgentId());
            metadata.put(SYMBOL, memory.getSymbol());
            metadata.put("direction", memory.getDirection().name());
            metadata.put("outcome", memory.getOutcome().name());
            metadata.put(TIMESTAMP, memory.getTimestamp().toEpochMilli());
            
            if (memory.getEntryPrice() > 0) {
                metadata.put("entryPrice", memory.getEntryPrice());
            }
            if (memory.getExitPrice() != null) {
                metadata.put("exitPrice", memory.getExitPrice());
            }
            if (memory.getProfitPercent() != null) {
                metadata.put("profitPercent", memory.getProfitPercent());
            }
            if (memory.getScenarioDescription() != null) {
                metadata.put("scenarioDescription", memory.getScenarioDescription());
            }
            if (memory.getLessonLearned() != null) {
                metadata.put("lessonLearned", memory.getLessonLearned());
            }
            
            // Build upsert request
            var vector = new PineconeVector(
                memory.getId(),
                memory.getEmbedding(),
                metadata
            );
            
            var request = new UpsertRequest(List.of(vector), namespace);
            
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(API_KEY, apiKey);
            
            var httpEntity = new HttpEntity<>(request, headers);
            
            restTemplate.exchange(
                baseUrl + "/vectors/upsert",
                HttpMethod.POST,
                httpEntity,
                UpsertResponse.class
            );
            
            logger.debug("Successfully stored memory {} in Pinecone", memory.getId());
            
        } catch (Exception e) {
            logger.error("Failed to store memory in Pinecone", e);
            throw new BotOperationException("store_memory", "Failed to store memory: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<TradeMemory> findSimilar(double[] queryEmbedding, String symbol, int topK) {
        return findSimilar(queryEmbedding, symbol, topK, 0.0, 0);
    }
    
    @Override
    public List<TradeMemory> findSimilar(
            double[] queryEmbedding,
            String symbol,
            int topK,
            double minSimilarity,
            int maxAgeDays) {
        
        try {
            logger.debug("Querying Pinecone for {} similar memories (symbol: {}, minSimilarity: {}, maxAgeDays: {})",
                topK, symbol, minSimilarity, maxAgeDays);
            
            // Build metadata filter
            Map<String, Object> filter = new HashMap<>();
            filter.put(SYMBOL, Map.of("$eq", symbol));
            
            if (maxAgeDays > 0) {
                long cutoffTimestamp = Instant.now()
                    .minus(maxAgeDays, ChronoUnit.DAYS)
                    .toEpochMilli();
                filter.put(TIMESTAMP, Map.of("$gte", cutoffTimestamp));
            }
            
            // Build query request
            var request = new QueryRequest(
                queryEmbedding,
                topK,
                namespace,
                filter,
                true,  // includeValues
                true   // includeMetadata
            );
            
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(API_KEY, apiKey);
            
            var httpEntity = new HttpEntity<>(request, headers);
            
            var response = restTemplate.exchange(
                baseUrl + "/query",
                HttpMethod.POST,
                httpEntity,
                QueryResponse.class
            ).getBody();
            
            if (response == null || response.matches() == null) {
                logger.warn("Pinecone returned empty response");
                return List.of();
            }
            
            // Convert matches to TradeExperience objects
            List<TradeMemory> memories = response.matches().stream()
                .filter(match -> match.score() >= minSimilarity)
                .map(match -> {
                    var metadata = match.metadata();
                    return TradeMemory.builder()
                        .id(match.id())
                        .agentId((String) metadata.get("agentId"))
                        .symbol((String) metadata.get(SYMBOL))
                        .scenarioDescription((String) metadata.get("scenarioDescription"))
                        .direction(TradeDirection.valueOf((String) metadata.get("direction")))
                        .entryPrice(getDoubleValue(metadata, "entryPrice"))
                        .exitPrice(getDoubleValue(metadata, "exitPrice"))
                        .outcome(TradeOutcome.valueOf((String) metadata.get("outcome")))
                        .profitPercent(getDoubleValue(metadata, "profitPercent"))
                        .lessonLearned((String) metadata.get("lessonLearned"))
                        .timestamp(Instant.ofEpochMilli(getLongValue(metadata, TIMESTAMP)))
                        .embedding(match.values())
                        .similarityScore(match.score())
                        .build();
                })
                .toList();
            
            logger.debug("Retrieved {} similar memories from Pinecone", memories.size());
            return memories;
            
        } catch (Exception e) {
            logger.error("Failed to query Pinecone", e);
            throw new BotOperationException("query_memories", "Failed to query memories: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void delete(String memoryId) {
        try {
            logger.debug("Deleting memory {} from Pinecone", memoryId);
            
            var request = new DeleteRequest(List.of(memoryId), namespace);
            
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(API_KEY, apiKey);
            
            var httpEntity = new HttpEntity<>(request, headers);
            
            restTemplate.exchange(
                baseUrl + "/vectors/delete",
                HttpMethod.POST,
                httpEntity,
                Void.class
            );
            
            logger.debug("Successfully deleted memory {} from Pinecone", memoryId);
            
        } catch (Exception e) {
            logger.error("Failed to delete memory from Pinecone", e);
            throw new BotOperationException("delete_memory", "Failed to delete memory: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isHealthy() {
        try {
            var headers = new HttpHeaders();
            headers.set(API_KEY, apiKey);
            var httpEntity = new HttpEntity<>(headers);
            
            restTemplate.exchange(
                baseUrl + "/describe_index_stats",
                HttpMethod.GET,
                httpEntity,
                Map.class
            );
            
            return true;
        } catch (Exception e) {
            logger.error("Pinecone health check failed", e);
            return false;
        }
    }
    
    // Helper methods
    
    private double getDoubleValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) return 0.0;
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(value.toString());
    }
    
    private long getLongValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }
    
    // DTOs for Pinecone API
    
    private record PineconeVector(
        String id,
        double[] values,
        Map<String, Object> metadata
    ) {}
    
    private record UpsertRequest(
        List<PineconeVector> vectors,
        String namespace
    ) {}
    
    private record UpsertResponse(
        @JsonProperty("upsertedCount") int upsertedCount
    ) {}
    
    private record QueryRequest(
        double[] vector,
        int topK,
        String namespace,
        Map<String, Object> filter,
        boolean includeValues,
        boolean includeMetadata
    ) {}
    
    private record QueryResponse(
        List<QueryMatch> matches,
        String namespace
    ) {}
    
    private record QueryMatch(
        String id,
        double score,
        double[] values,
        Map<String, Object> metadata
    ) {}
    
    private record DeleteRequest(
        List<String> ids,
        String namespace
    ) {}
}
