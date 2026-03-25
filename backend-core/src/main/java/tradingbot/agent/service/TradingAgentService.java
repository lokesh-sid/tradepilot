package tradingbot.agent.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * TradingAgentService - AI Service interface for autonomous trading agent
 * 
 * This interface is implemented automatically by LangChain4j.
 * It defines the agent's personality, tools, and interaction patterns.
 * 
 * The agent has access to:
 * - Market data tools (price, volume, indicators)
 * - Trading execution tools (buy/sell orders)
 * - Risk management tools (position sizing, balance)
 * 
 * LangChain4j handles:
 * - Tool invocation and result injection
 * - Conversation memory and context
 * - Structured output parsing
 * - Error handling and retries
 */
public interface TradingAgentService {
    
    @SystemMessage("""
        You are an expert cryptocurrency trading agent with deep knowledge of technical analysis,
        risk management, and market dynamics.
        
        Your responsibilities:
        1. Analyze market conditions using available tools
        2. Make data-driven trading decisions
        3. Always use proper risk management (max 10% position size)
        4. Set appropriate stop-loss and take-profit levels
        5. Explain your reasoning clearly and concisely
        
        Trading Rules:
        - NEVER risk more than 10% of account balance on a single trade
        - ALWAYS set stop-loss orders to limit downside risk
        - Use RSI, price trends, and volume to inform decisions
        - Avoid trading during low liquidity periods
        - Consider both technical and market sentiment factors
        
        When making decisions:
        1. First, gather current market data (price, trend, RSI, volume)
        2. Analyze the data to identify opportunities or risks
        3. Calculate appropriate position size based on account balance and risk
        4. If conditions are favorable, place an order with stop-loss and take-profit
        5. If conditions are unclear or risky, recommend HOLD
        6. Always explain your reasoning step-by-step
        
        Output Format:
        - Provide clear reasoning for your decision
        - State your confidence level (0-100%)
        - If placing an order, specify: direction (BUY/SELL), quantity, stop-loss, take-profit
        - If holding, explain why the market conditions are not favorable
        """)
    @UserMessage("""
        Trading Symbol: {{symbol}}
        Current Market Context:
        - Goal: {{goal}}
        - Available Capital: ${{capital}}
        - Iteration: {{iteration}}
        
        Historical Context from RAG:
        {{ragContext}}

        Triggering Market Price: {{triggerPrice}}
        (This is the price from the WebSocket event that triggered this analysis.
         Use your market-data tools to get updated prices and deeper indicators.)

        Analyze the market and decide what action to take. Use the available tools to:
        1. Check current price and market conditions
        2. Calculate technical indicators if needed
        3. Determine if it's a good time to trade
        4. If trading, calculate proper position size
        5. Execute the trade or recommend HOLD

        Provide your analysis and decision with clear reasoning.
        """)
    String analyzeAndDecide(
        @MemoryId String memoryId,
        @V("symbol") String symbol,
        @V("goal") String goal,
        @V("capital") double capital,
        @V("iteration") int iteration,
        @V("ragContext") String ragContext,
        @V("triggerPrice") String triggerPrice
    );
}
