package tradingbot.agent.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * TradeReflectionService - LangChain4j AI service for post-trade self-reflection.
 *
 * This is a lightweight, stateless (no chat memory) LLM service whose sole
 * responsibility is to generate a concise "lesson learned" after a position closes.
 *
 * It is deliberately separate from {@link TradingAgentService} so that:
 * - It carries no conversation history (reflection is stateless)
 * - It uses a lower token budget (focused single-purpose prompt)
 * - It can run on any background thread without polluting agent chat memory
 *
 * Implemented automatically by LangChain4j via {@link dev.langchain4j.service.AiServices}.
 * Registered as a Spring bean in {@link tradingbot.agent.config.LangChain4jConfig}.
 */
public interface TradeReflectionService {

    @SystemMessage("""
        You are a professional trading coach performing a concise post-trade debrief.
        Your role is to extract one actionable lesson from a completed trade.

        Rules:
        - Be brutally honest about what went right or wrong.
        - The lesson must be specific, not generic ("avoid high-risk trades" is NOT acceptable).
        - Limit your response to ONE sentence of 30 words or fewer.
        - Do not include greetings, preamble, or explanations — output ONLY the lesson.
        """)
    @UserMessage("""
        Trade Summary:
        - Symbol: {{symbol}}
        - Direction: {{direction}}
        - Entry Price: ${{entryPrice}}
        - Exit Price:  ${{exitPrice}}
        - Realized PnL: {{pnlPercent}}%

        Original Pre-Trade Reasoning:
        {{originalReasoning}}

        Based on the above, provide ONE concise lesson learned (30 words max).
        """)
    String generateLesson(
        @V("symbol") String symbol,
        @V("direction") String direction,
        @V("entryPrice") String entryPrice,
        @V("exitPrice") String exitPrice,
        @V("pnlPercent") String pnlPercent,
        @V("originalReasoning") String originalReasoning
    );
}
