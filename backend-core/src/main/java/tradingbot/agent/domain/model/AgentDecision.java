package tradingbot.agent.domain.model;

import java.time.Instant;

/**
 * AgentDecision — the output produced by an agent after processing one
 * {@link tradingbot.domain.market.KlineClosedEvent}.
 *
 * <p>This is an immutable value object.  Callers must not mutate any field
 * after construction.
 *
 * @param agentId        identifier of the agent that produced this decision
 * @param symbol         trading pair this decision applies to
 * @param action         {@code BUY}, {@code SELL}, or {@code HOLD}
 * @param confidence     0–100 confidence score returned by the LLM / strategy
 * @param reasoning      human-readable explanation produced by the LLM
 * @param decidedAt      wall-clock time when the decision was produced
 */
public record AgentDecision(
    String agentId,
    String symbol,
    Action action,
    int confidence,
    String reasoning,
    Instant decidedAt,
    Double quantity,
    Double stopLossPercent,
    Double takeProfitPercent
) {
    /**
     * Possible trading actions an agent can emit.
     */
    public enum Action {
        BUY, SELL, HOLD
    }

    /**
     * Convenience factory — sets {@code decidedAt} to {@link Instant#now()}.
     */
    public static AgentDecision of(
            String agentId,
            String symbol,
            Action action,
            int confidence,
            String reasoning) {
        return new AgentDecision(agentId, symbol, action, confidence, reasoning, Instant.now(), null, null, null);
    }

    public static AgentDecision of(
            String agentId,
            String symbol,
            Action action,
            int confidence,
            String reasoning,
            Double quantity,
            Double stopLossPercent,
            Double takeProfitPercent) {
        return new AgentDecision(agentId, symbol, action, confidence, reasoning, Instant.now(), quantity, stopLossPercent, takeProfitPercent);
    }

    /** Returns true when the agent recommends entering a position. */
    public boolean isEntry() {
        return action == Action.BUY || action == Action.SELL;
    }

    public Double quantity() {
        return quantity;
    }

    public Double stopLossPercent() {
        return stopLossPercent;
    }

    public Double takeProfitPercent() {
        return takeProfitPercent;
    }
}
