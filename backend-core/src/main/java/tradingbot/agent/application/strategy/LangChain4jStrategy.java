package tradingbot.agent.application.strategy;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.TradeDirection;
import tradingbot.agent.domain.model.TradeMemory;
import tradingbot.agent.domain.model.TradeOutcome;
import tradingbot.agent.config.AgentExecutionContext;
import tradingbot.agent.config.ExchangeServiceRegistry;
import tradingbot.agent.service.RAGService;
import tradingbot.agent.service.TradingAgentService;
import tradingbot.domain.market.MarketEvent;

/**
 * LangChain4j-based agentic strategy
 * 
 * The agent autonomously:
 * - Calls tools to gather market data
 * - Analyzes conditions using reasoning
 * - Places orders through tool invocation
 * - Maintains context across calls
 * - Learns from historical trades via RAG
 */
@Component
public class LangChain4jStrategy implements AgentStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(LangChain4jStrategy.class);
    
    private final TradingAgentService tradingAgentService;
    private final RAGService ragService;
    private final ExchangeServiceRegistry exchangeServiceRegistry;
    private final AgentExecutionContext executionContext;

    @Value("${rag.enabled:true}")
    private boolean ragEnabled;

    @Value("${rag.strategy.context-limit:3}")
    private int ragContextLimit;

    public LangChain4jStrategy(
            TradingAgentService tradingAgentService,
            RAGService ragService,
            ExchangeServiceRegistry exchangeServiceRegistry,
            AgentExecutionContext executionContext) {
        this.tradingAgentService = tradingAgentService;
        this.ragService = ragService;
        this.exchangeServiceRegistry = exchangeServiceRegistry;
        this.executionContext = executionContext;
    }
    
    @Override
    public void executeIteration(Agent agent, MarketEvent triggeringEvent) {
        logger.info("[AGENTIC] Agent {} analyzing market with tool access (triggerPrice={})",
                agent.getId(), triggeringEvent != null ? triggeringEvent.price() : "n/a");

        // 1. Prepare RAG context (historical learnings)
        String ragContext = prepareRAGContext(agent);

        // 2. Surface the trigger price so the LLM prompt has it without a tool call
        String triggerPrice = (triggeringEvent != null)
                ? String.format("$%.2f", triggeringEvent.price().doubleValue())
                : "unknown (polling mode — use market-data tool)";

        // 3. Invoke the agent - it will autonomously call tools and make decisions
        executionContext.set(exchangeServiceRegistry.resolve(agent.getExchangeName()));
        String agentResponse;
        try {
            agentResponse = tradingAgentService.analyzeAndDecide(
                agent.getId().getValue(),
                agent.getTradingSymbol(),
                agent.getGoal().toString(),
                agent.getCapital(),
                agent.getState().getIterationCount(),
                ragContext,
                triggerPrice
            );
        } finally {
            executionContext.clear();
        }
        
        logger.info("Agent {} decision: {}", agent.getId(), agentResponse);
        
        // 3. Update agent state
        agent.getState().incrementIteration();

        // 4. Parse and store the reasoning
        Reasoning reasoning = parseAgentResponse(agentResponse);
        agent.reason(reasoning);
        
        // 5. Store this experience in RAG for future learning
        if (ragEnabled) {
            storeExperience(agent, reasoning);
        }
        
        logAgentDecision(agent, agentResponse);
    }
    
    @Override
    public String getStrategyName() {
        return "LangChain4j Agentic";
    }
    
    /**
     * Format RAG context from similar historical trades
     */
    private String prepareRAGContext(Agent agent) {
        if (!ragEnabled) {
            return "";
        }
        
        // Construct query context from agent state
        String queryContext = String.format("%s %s goal:%s", 
            agent.getTradingSymbol(),
            agent.getGoal().getType(),
            agent.getGoal().getDescription()
        );
        
        // Retrieve similar trades from RAG service
        List<TradeMemory> similarTrades;
        try {
            similarTrades = ragService.retrieveSimilarTrades(queryContext, ragContextLimit);
        } catch (Exception e) {
            logger.warn("Failed to retrieve RAG context for agent {}: {}", agent.getId(), e.getMessage());
            return "Historical trading data unavailable due to service error.";
        }
        
        if (similarTrades.isEmpty()) {
            return "No historical trading data available yet.";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("Historical Trading Experiences:\n\n");
        
        for (int i = 0; i < similarTrades.size(); i++) {
            TradeMemory memory = similarTrades.get(i);
            context.append(String.format("%d. %s - %s%n", 
                i + 1, memory.getSymbol(), memory.getOutcome()));
            
            // Safe null handling
            String direction = memory.getDirection() != null ? memory.getDirection().name() : "UNKNOWN";
            context.append(String.format("   Direction: %s%n", direction));
            context.append(String.format("   Entry: $%.2f, Exit: $%.2f%n", 
                memory.getEntryPrice(), memory.getExitPrice()));
            context.append(String.format("   Profit: %.2f%%%n", memory.getProfitPercent()));
            context.append(String.format("   Lesson: %s%n%n", memory.getLessonLearned()));
        }
        
        logger.debug("Retrieved {} similar historical trades for agent {}", 
            similarTrades.size(), agent.getId());
        
        return context.toString();
    }
    
    /**
     * Parse agent response into Reasoning domain object
     */
    private Reasoning parseAgentResponse(String agentResponse) {
        int confidence = 70;
        // Case-insensitive check and split
        if (agentResponse.toLowerCase().contains("confidence")) {
            String[] parts = agentResponse.split("(?i)confidence[:\\s]+");
            if (parts.length > 1) {
                String confStr = parts[1].replaceAll("\\D", "");
                if (!confStr.isEmpty()) {
                    confidence = Integer.parseInt(confStr.substring(0, Math.min(2, confStr.length())));
                }
            }
        }
        
        String recommendation = "HOLD";
        if (agentResponse.contains("BUY")) {
            recommendation = "BUY";
        } else if (agentResponse.contains("SELL")) {
            recommendation = "SELL";
        }

        return new Reasoning(
            "Market analysis completed",
            agentResponse,
            "Agent used tools to analyze market and make decision",
            recommendation,
            confidence,
            Instant.now()
        );
    }
    
    /**
     * Store this trading experience in RAG for future learning
     * Note: This stores the DECISION intent. The actual trade outcome
     * will be updated later when the position closes.
     */
    private void storeExperience(Agent agent, Reasoning reasoning) {
        try {
            // Build scenario description for semantic embedding
            String scenarioDescription = String.format(
                "%s %s decision with %d%% confidence. Risk: %s",
                agent.getTradingSymbol(),
                reasoning.getRecommendation(),
                reasoning.getConfidence(),
                reasoning.getRiskAssessment()
            );
            
            // Map recommendation to trade direction
            TradeDirection direction = mapRecommendationToDirection(reasoning.getRecommendation());
            
            // Store trade memory (entry/exit prices and outcome will be updated later)
            ragService.storeTradeMemory(
                agent.getId().toString(),
                agent.getTradingSymbol(),
                scenarioDescription,
                direction,
                0.0, // entryPrice - will be updated when order fills
                null, // exitPrice - will be updated when position closes
                TradeOutcome.PENDING, // outcome - will be updated on trade completion
                null, // profitPercent - calculated on exit
                reasoning.getAnalysis(), // Store reasoning as initial lesson
                null // networkFee - not known yet
            );
            
            logger.debug("Stored trade decision memory for agent {} - {} with confidence {}%", 
                agent.getId(), 
                reasoning.getRecommendation(),
                reasoning.getConfidence());
        } catch (Exception e) {
            logger.warn("Failed to store trade memory for agent {}: {}", 
                agent.getId(), e.getMessage());
        }
    }
    
    /**
     * Map recommendation string to trade direction
     */
    private TradeDirection mapRecommendationToDirection(String recommendation) {
        return switch (recommendation.toUpperCase()) {
            case "BUY" -> TradeDirection.LONG;
            case "SELL" -> TradeDirection.SHORT;
            default -> null; // HOLD means no direction
        };
    }
    
    /**
     * Log the agent's decision
     */
    private void logAgentDecision(Agent agent, String decision) {
        logger.info("""
            
            ╔════════════════════════════════════════════════════════════════╗
            ║ AGENTIC DECISION (LangChain4j)                                 ║
            ╠════════════════════════════════════════════════════════════════╣
            ║ Agent: {} ({})
            ║ Symbol: {}
            ║ Time: {}
            ╠════════════════════════════════════════════════════════════════╣
            ║ DECISION:
            ║ {}
            ╚════════════════════════════════════════════════════════════════╝
            """,
            agent.getName(), agent.getId(),
            agent.getTradingSymbol(),
            Instant.now(),
            decision
        );
    }
}
