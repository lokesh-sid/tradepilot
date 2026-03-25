package tradingbot.agent.infrastructure.llm;

import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.ReasoningContext;

/**
 * LLMProvider - Interface for LLM providers (Grok, GPT-4, etc.)
 */
public interface LLMProvider {
    
    /**
     * Generate trading reasoning based on market context
     */
    Reasoning generateReasoning(ReasoningContext context);
    
    /**
     * Check if provider is enabled
     */
    boolean isEnabled();
    
    /**
     * Get provider name
     */
    String getProviderName();
}
