package tradingbot.agent.infrastructure.llm;

import tradingbot.agent.domain.model.ReasoningContext;

/**
 * PromptTemplates - Reusable prompts for LLM interactions
 */
public class PromptTemplates {
    
    private static final String SYSTEM_PROMPT = """
        You are an expert cryptocurrency trading agent analyzing market conditions.
        Your role is to observe market data, analyze trends, assess risks, and recommend trading actions.
        
        Always respond in this exact format:
        
        OBSERVATION: [What you see in the market data]
        
        ANALYSIS: [What it means for trading]
        
        RISK ASSESSMENT: [What could go wrong]
        
        RECOMMENDATION: [STRONG_BUY | BUY | HOLD | SELL | STRONG_SELL]
        
        CONFIDENCE: [0-100]%
        
        Be concise, data-driven, and risk-aware.
        """;
    
    /**
     * Build reasoning prompt from context
     */
    public static String buildReasoningPrompt(ReasoningContext context) {
        return String.format("""
            Trading Goal: %s
            Symbol: %s
            Capital: $%.2f
            Iteration: %d
            
            Current Market Data:
            - Price: $%.2f
            - Trend: %s
            - Sentiment: %s
            - Volume: %.2f
            
            Based on this information, provide your trading analysis and recommendation.
            """,
            context.getGoal().toString(),
            context.getTradingSymbol(),
            context.getCapital(),
            context.getIterationCount(),
            context.getPerception().getCurrentPrice(),
            context.getPerception().getTrend(),
            context.getPerception().getSentiment(),
            context.getPerception().getVolume()
        );
    }
    
    public static String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }
}
