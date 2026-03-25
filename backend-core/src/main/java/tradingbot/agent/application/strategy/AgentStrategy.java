package tradingbot.agent.application.strategy;

import tradingbot.agent.domain.model.Agent;
import tradingbot.domain.market.MarketEvent;

/**
 * Strategy interface for different agent reasoning approaches
 *
 * Implementations:
 * - LangChain4jStrategy: Agentic framework with tool use
 * - RAGEnhancedStrategy: RAG + manual LLM
 * - LegacyLLMStrategy: Original implementation
 */
public interface AgentStrategy {

    /**
     * Execute one iteration of the agent loop.
     *
     * @param agent          the agent to run
     * @param triggeringEvent the market event that triggered this iteration,
     *                        or {@code null} when invoked from the polling path
     */
    void executeIteration(Agent agent, MarketEvent triggeringEvent);
    
    /**
     * Get the name of this strategy
     */
    String getStrategyName();
}
