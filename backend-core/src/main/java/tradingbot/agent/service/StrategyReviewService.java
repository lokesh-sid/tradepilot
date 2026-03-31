package tradingbot.agent.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * StrategyReviewService — LangChain4j AI service for periodic batch strategy review.
 *
 * <p>Stateless (no chat memory), single-shot call. Receives a formatted summary of
 * recent closed trades and returns structured insights: identified bias patterns and
 * suggested parameter adjustments.
 *
 * <p>Implemented automatically by LangChain4j via {@link dev.langchain4j.service.AiServices}.
 * Registered as a Spring bean in {@link tradingbot.agent.config.LangChain4jConfig}.
 * Called by {@link tradingbot.agent.application.LLMStrategyReviewService}.
 */
public interface StrategyReviewService {

    @SystemMessage("""
        You are a quantitative trading analyst reviewing an AI agent's recent trade history.
        Your job is to identify systematic patterns, biases, and actionable improvements.

        Rules:
        - Be specific: reference actual numbers from the trade data (e.g. "8 of 10 high-confidence LONG trades on BTCUSDT lost").
        - Identify at most 3 distinct patterns.
        - For each pattern, suggest one concrete parameter adjustment (e.g. "reduce confidence threshold from 70 to 80 for LONG entries").
        - Flag any trade that looks like an outlier worth human review.
        - Output as plain text, no markdown headers, max 300 words.
        """)
    @UserMessage("""
        Agent ID: {{agentId}}
        Review period: {{period}}

        Recent closed trades (CSV format — symbol,direction,confidence,entryPrice,exitPrice,pnlPercent,outcome,tags):
        {{tradeHistory}}

        Identify patterns, biases, and suggest specific parameter adjustments.
        """)
    String analyzeTradePatterns(
        @V("agentId")      String agentId,
        @V("period")       String period,
        @V("tradeHistory") String tradeHistory
    );
}
