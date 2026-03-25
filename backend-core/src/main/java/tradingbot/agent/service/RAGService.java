package tradingbot.agent.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.ReasoningContext;
import tradingbot.agent.domain.model.TradeDirection;
import tradingbot.agent.domain.model.TradeMemory;
import tradingbot.agent.domain.model.TradeOutcome;
import tradingbot.agent.infrastructure.llm.LLMProvider;
import tradingbot.agent.infrastructure.persistence.TradeMemoryEntity;
import tradingbot.agent.infrastructure.repository.TradeMemoryRepository;

/**
 * RAGService - Orchestrates the Retrieval-Augmented Generation pipeline
 * 
 * This service implements the full RAG workflow:
 * 1. Embed the current scenario
 * 2. Retrieve similar past experiences from vector DB
 * 3. Build augmented context with memories
 * 4. Generate reasoning with LLM
 * 5. Store the new experience as a memory
 */
@Service
public class RAGService {
    
    private static final Logger logger = LoggerFactory.getLogger(RAGService.class);
    
    private final EmbeddingService embeddingService;
    private final MemoryStoreService memoryStore;
    private final ContextBuilder contextBuilder;
    private final Optional<LLMProvider> llmProvider;
    private final TradeMemoryRepository tradeMemoryRepository;

    @Value("${rag.retrieval.top-k:5}")
    private int retrievalTopK;
    
    @Value("${rag.retrieval.similarity-threshold:0.7}")
    private double similarityThreshold;
    
    @Value("${rag.retrieval.max-age-days:90}")
    private int maxAgeDays;
    
    public RAGService(
            EmbeddingService embeddingService,
            MemoryStoreService memoryStore,
            ContextBuilder contextBuilder,
            Optional<LLMProvider> llmProvider,
            TradeMemoryRepository tradeMemoryRepository) {
        this.embeddingService = embeddingService;
        this.memoryStore = memoryStore;
        this.contextBuilder = contextBuilder;
        this.llmProvider = llmProvider;
        this.tradeMemoryRepository = tradeMemoryRepository;
    }
    
    /**
     * Generate reasoning with RAG enhancement
     * 
     * @param agent The agent making the decision
     * @param context The current market context
     * @return LLM-generated reasoning augmented with historical memories
     */
    public Reasoning generateReasoningWithRAG(Agent agent, ReasoningContext context) {
        if (llmProvider.isEmpty()) {
            logger.debug("No LLM provider configured — RAG reasoning skipped for agent {}", agent.getId());
            return new Reasoning("no-llm", "LLM not configured", "n/a", "HOLD", 0, Instant.now());
        }
        try {
            logger.info("Starting RAG-enhanced reasoning for agent {} on symbol {}",
                agent.getId(), context.getTradingSymbol());
            
            // Step 1: Build scenario description and embed it
            String scenarioDescription = contextBuilder.buildScenarioDescription(
                context.getPerception()
            );
            logger.debug("Scenario: {}", scenarioDescription);
            
            double[] queryEmbedding = embeddingService.embed(scenarioDescription);
            logger.debug("Generated embedding with {} dimensions", queryEmbedding.length);
            
            // Step 2: Retrieve similar memories
            List<TradeMemory> similarMemories = memoryStore.findSimilar(
                queryEmbedding,
                context.getTradingSymbol(),
                retrievalTopK,
                similarityThreshold,
                maxAgeDays
            );
            
            logger.info("Retrieved {} similar memories (threshold: {}, maxAge: {} days)",
                similarMemories.size(), similarityThreshold, maxAgeDays);
            
            // Step 3: Build augmented context with memories
            // Create a new ReasoningContext with augmented prompt in perception
            ReasoningContext augmentedReasoningContext = createAugmentedContext(
                context,
                similarMemories
            );
            
            // Step 4: Generate reasoning with LLM
            logger.debug("Calling LLM with augmented context");
            Reasoning reasoning = llmProvider.get().generateReasoning(augmentedReasoningContext);
            
            logger.info("Generated reasoning with confidence: {}%", reasoning.getConfidence());
            
            // Step 5: Store this experience as a memory (async)
            // Note: We store it immediately with PENDING outcome
            // It will be updated later when the trade completes
            storeMemoryAsync(
                agent.getId().toString(),
                context,
                scenarioDescription,
                queryEmbedding,
                reasoning
            );
            
            return reasoning;
            
        } catch (Exception e) {
            logger.error("RAG pipeline failed, falling back to basic reasoning", e);
            // Fallback: generate reasoning without RAG
            return llmProvider.get().generateReasoning(context);
        }
    }
    
    /**
     * Create an augmented reasoning context with memories
     */
    private ReasoningContext createAugmentedContext(
            ReasoningContext baseContext,
            List<TradeMemory> memories) {
        
        // Build augmented perception with memory context
        String augmentedText = contextBuilder.buildAugmentedContext(
            baseContext,
            memories
        );
        
        // Create new perception with augmented sentiment field
        Perception augmentedPerception = new Perception(
            baseContext.getPerception().getSymbol(),
            baseContext.getPerception().getCurrentPrice(),
            baseContext.getPerception().getTrend(),
            augmentedText,  // Store augmented context in sentiment field
            baseContext.getPerception().getVolume(),
            baseContext.getPerception().getTimestamp()
        );
        
        // Return new context with augmented perception
        return new ReasoningContext(
            baseContext.getGoal(),
            augmentedPerception,
            baseContext.getTradingSymbol(),
            baseContext.getCapital(),
            baseContext.getIterationCount()
        );
    }
    
    /**
     * Retrieve similar historical trades based on query context
     * 
     * @param queryContext Search context (symbol + goal + market conditions)
     * @param limit Maximum number of similar trades to return
     * @return List of similar trade memories ordered by relevance
     */
    public List<TradeMemory> retrieveSimilarTrades(String queryContext, int limit) {
        if (!isHealthy()) {
            logger.warn("RAG system not healthy, returning empty list");
            return List.of();
        }
        
        try {
            // Generate embedding for query context
            double[] queryEmbedding = embeddingService.embed(queryContext);
            
            // Retrieve similar memories from vector store
            return memoryStore.findSimilar(
                queryEmbedding, 
                null, // symbol filter (null = all symbols)
                limit, 
                similarityThreshold,
                maxAgeDays
            );
        } catch (Exception e) {
            logger.error("Error retrieving similar trades: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Store a completed trade as a memory for future retrieval
     * 
     * @param agentId The agent who made the trade
     * @param symbol Trading symbol
     * @param scenarioDescription Description of market conditions
     * @param direction Trade direction (LONG/SHORT)
     * @param entryPrice Entry price
     * @param exitPrice Exit price (null if still open)
     * @param outcome Trade outcome
     * @param profitPercent Profit/loss percentage
     * @param lessonLearned What was learned from this trade
     * @param networkFee Fees paid (Gas + Protocol). Important for DEX calculation.
     */
    public void storeTradeMemory(
            String agentId,
            String symbol,
            String scenarioDescription,
            TradeDirection direction,
            double entryPrice,
            Double exitPrice,
            TradeOutcome outcome,
            Double profitPercent,
            String lessonLearned,
            Double networkFee) {
        
        try {
            logger.info("Storing trade memory for agent {} on {}", agentId, symbol);
            
            // Adjust logic to include fees in the memory or lesson if significant
            String enhancedLesson = lessonLearned;
            if (networkFee != null && networkFee > 0) {
                 enhancedLesson += String.format(" [Fees: %.4f]", networkFee);
            }

            // Generate embedding for the scenario
            double[] embedding = embeddingService.embed(scenarioDescription);
            
            // Build memory
            TradeMemory memory = TradeMemory.builder()
                .id(UUID.randomUUID().toString())
                .agentId(agentId)
                .symbol(symbol)
                .scenarioDescription(scenarioDescription)
                .direction(direction)
                .entryPrice(entryPrice)
                .exitPrice(exitPrice)
                .outcome(outcome)
                .profitPercent(profitPercent)
                .lessonLearned(enhancedLesson)
                .networkFee(networkFee)
                .timestamp(Instant.now())
                .embedding(embedding)
                .build();
            
            // Store in vector database
            memoryStore.store(memory);
            
            logger.info("Successfully stored trade memory {}", memory.getId());
            
        } catch (Exception e) {
            logger.error("Failed to store trade memory", e);
            // Don't throw - memory storage failure shouldn't break trading
        }
    }
    
    /**
     * Store a memory asynchronously (non-blocking)
     */
    private void storeMemoryAsync(
            String agentId,
            ReasoningContext context,
            String scenarioDescription,
            double[] embedding,
            Reasoning reasoning) {
        
        // Store immediately with PENDING status
        // This will be updated later when the trade executes/completes
        try {
            TradeMemory memory = TradeMemory.builder()
                .id(UUID.randomUUID().toString())
                .agentId(agentId)
                .symbol(context.getTradingSymbol())
                .scenarioDescription(scenarioDescription)
                .direction(extractDirection(reasoning))
                .entryPrice(context.getPerception().getCurrentPrice())
                .exitPrice(null)  // Will be updated later
                .outcome(TradeOutcome.PENDING)
                .profitPercent(null)  // Will be updated later
                .lessonLearned(reasoning.getAnalysis())  // Initial analysis
                .timestamp(Instant.now())
                .embedding(embedding)
                .build();
            
            memoryStore.store(memory);
            logger.debug("Stored pending memory {}", memory.getId());
            
        } catch (Exception e) {
            logger.warn("Failed to store pending memory", e);
        }
    }
    
    /**
     * Extract trade direction from reasoning
     * Defaults to LONG if unclear
     */
    private TradeDirection extractDirection(Reasoning reasoning) {
        String recommendation = reasoning.getRecommendation().toLowerCase();
        if (recommendation.contains("short") || recommendation.contains("sell")) {
            return TradeDirection.SHORT;
        }
        return TradeDirection.LONG;  // Default to LONG for buy/hold
    }
    
    /**
     * Check if RAG system is healthy
     */
    public boolean isHealthy() {
        return memoryStore.isHealthy();
    }

    /**
     * Update a PENDING trade memory with the real outcome and LLM-generated lesson.
     *
     * Strategy:
     * 1. Find the most recent PENDING record for this agent+symbol in PostgreSQL.
     * 2. Delete the old embedding from the vector store (stale PENDING entry).
     * 3. Re-embed the updated scenario (includes real outcome + lesson).
     * 4. Store a new vector entry with the final state.
     * 5. Update the SQL metadata record.
     *
     * If no PENDING record is found, a brand-new memory is created instead.
     *
     * @param agentId       Agent that made the trade
     * @param symbol        Trading pair (e.g. BTCUSDT)
     * @param direction     LONG or SHORT
     * @param entryPrice    Actual fill price
     * @param exitPrice     Price at which position was closed
     * @param outcome       PROFIT / LOSS / BREAKEVEN
     * @param profitPercent Realized PnL as a percentage
     * @param lessonLearned LLM-generated reflection sentence
     */
    public void updateTradeReflection(
            String agentId,
            String symbol,
            TradeMemoryEntity.Direction direction,
            double entryPrice,
            double exitPrice,
            TradeMemoryEntity.Outcome outcome,
            double profitPercent,
            String lessonLearned) {

        try {
            logger.info("Updating trade reflection for agent {} on {} — outcome: {}",
                    agentId, symbol, outcome);

            // 1. Find the most recent PENDING SQL record
            var pendingRecords = tradeMemoryRepository.findPendingByAgentIdAndSymbol(agentId, symbol);

            // 2. Delete old vector entry if one existed
            if (!pendingRecords.isEmpty()) {
                TradeMemoryEntity pending = pendingRecords.get(0);
                try {
                    memoryStore.delete(pending.getId());
                    logger.debug("Deleted stale PENDING vector entry {}", pending.getId());
                } catch (Exception ex) {
                    logger.warn("Could not delete pending vector entry {}: {}", pending.getId(), ex.getMessage());
                }

                // 3. Update SQL record in-place
                pending.setEntryPrice(entryPrice);
                pending.setExitPrice(exitPrice);
                pending.setOutcome(outcome);
                pending.setProfitPercent(profitPercent);
                pending.setLessonLearned(lessonLearned);
                tradeMemoryRepository.save(pending);
                logger.debug("Updated SQL trade memory record {}", pending.getId());

                // 4. Re-embed + re-store the updated entry as a new vector
                String updatedScenario = String.format(
                    "%s %s trade — entry $%.2f, exit $%.2f, PnL %.2f%%. Lesson: %s",
                    symbol, direction.name(), entryPrice, exitPrice, profitPercent, lessonLearned);
                double[] newEmbedding = embeddingService.embed(updatedScenario);

                TradeOutcome tradeOutcome = mapEntityOutcomeToModel(outcome);
                TradeDirection tradeDirection = direction == TradeMemoryEntity.Direction.LONG
                        ? TradeDirection.LONG : TradeDirection.SHORT;

                TradeMemory updated = TradeMemory.builder()
                    .id(pending.getId())
                    .agentId(agentId)
                    .symbol(symbol)
                    .scenarioDescription(updatedScenario)
                    .direction(tradeDirection)
                    .entryPrice(entryPrice)
                    .exitPrice(exitPrice)
                    .outcome(tradeOutcome)
                    .profitPercent(profitPercent)
                    .lessonLearned(lessonLearned)
                    .timestamp(pending.getTimestamp())
                    .embedding(newEmbedding)
                    .build();

                memoryStore.store(updated);
                logger.info("Re-stored updated trade memory {} with outcome {}", pending.getId(), outcome);

            } else {
                // No PENDING record — store as a fresh memory
                logger.warn("No PENDING memory found for agent {} on {}. Storing as new entry.", agentId, symbol);
                TradeOutcome tradeOutcome = mapEntityOutcomeToModel(outcome);
                TradeDirection tradeDirection = direction == TradeMemoryEntity.Direction.LONG
                        ? TradeDirection.LONG : TradeDirection.SHORT;
                storeTradeMemory(agentId, symbol,
                        String.format("%s %s completed trade", symbol, direction),
                        tradeDirection, entryPrice, exitPrice, tradeOutcome,
                        profitPercent, lessonLearned, null);
            }

        } catch (Exception e) {
            logger.error("Failed to update trade reflection for agent {} on {}: {}", agentId, symbol, e.getMessage(), e);
            // Non-fatal: memory update failure must not disrupt the trading engine
        }
    }

    /**
     * Map persistence-layer Outcome enum to the domain-model TradeOutcome enum.
     */
    private TradeOutcome mapEntityOutcomeToModel(TradeMemoryEntity.Outcome outcome) {
        return switch (outcome) {
            case PROFIT -> TradeOutcome.PROFIT;
            case LOSS -> TradeOutcome.LOSS;
            case BREAKEVEN -> TradeOutcome.BREAKEVEN;
            case CANCELLED -> TradeOutcome.CANCELLED;
            default -> TradeOutcome.PENDING;
        };
    }
}
