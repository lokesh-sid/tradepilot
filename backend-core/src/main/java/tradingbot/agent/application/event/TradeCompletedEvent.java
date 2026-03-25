package tradingbot.agent.application.event;

import tradingbot.agent.infrastructure.persistence.TradeMemoryEntity;

/**
 * TradeCompletedEvent - Spring application event published when a position closes.
 *
 * Published by {@link tradingbot.agent.application.AgentOrchestrator} after a
 * successful EXIT_LONG or EXIT_SHORT execution is persisted.
 *
 * Consumed asynchronously by {@link tradingbot.agent.application.TradeReflectionService}
 * which calls the LLM to generate a "lesson learned" and updates the agent's
 * long-term RAG memory without blocking the main trading thread.
 */
public class TradeCompletedEvent {

    private final String agentId;
    private final String symbol;
    private final TradeMemoryEntity.Direction direction;
    private final double entryPrice;
    private final double exitPrice;
    private final double realizedPnlPercent;
    private final String originalReasoning;

    public TradeCompletedEvent(
            String agentId,
            String symbol,
            TradeMemoryEntity.Direction direction,
            double entryPrice,
            double exitPrice,
            double realizedPnlPercent,
            String originalReasoning) {
        this.agentId = agentId;
        this.symbol = symbol;
        this.direction = direction;
        this.entryPrice = entryPrice;
        this.exitPrice = exitPrice;
        this.realizedPnlPercent = realizedPnlPercent;
        this.originalReasoning = originalReasoning;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getSymbol() {
        return symbol;
    }

    public TradeMemoryEntity.Direction getDirection() {
        return direction;
    }

    public double getEntryPrice() {
        return entryPrice;
    }

    public double getExitPrice() {
        return exitPrice;
    }

    public double getRealizedPnlPercent() {
        return realizedPnlPercent;
    }

    public String getOriginalReasoning() {
        return originalReasoning;
    }
}
