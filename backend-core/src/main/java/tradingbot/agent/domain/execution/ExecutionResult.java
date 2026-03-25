package tradingbot.agent.domain.execution;

import java.time.Instant;

/**
 * ExecutionResult — outcome of an {@link OrderExecutionGateway#execute} call.
 *
 * <p>Captures what the gateway did (or chose not to do) so that callers can
 * log, audit, or feed the result back into the agent's risk context.
 *
 * @param action          what was attempted: ENTER_LONG, EXIT_LONG, ENTER_SHORT, EXIT_SHORT, NOOP
 * @param success         whether the order was filled (always true for NOOP)
 * @param exchangeOrderId order ID from the exchange (null for NOOP)
 * @param symbol          trading pair
 * @param fillPrice       average fill price (0 for NOOP)
 * @param fillQuantity    quantity filled (0 for NOOP)
 * @param realizedPnl     PnL from closing a position (0 for entries and NOOP)
 * @param reason          human-readable explanation
 * @param timestamp        when the execution occurred
 */
public record ExecutionResult(
        ExecutionAction action,
        boolean success,
        String exchangeOrderId,
        String symbol,
        double fillPrice,
        double fillQuantity,
        double realizedPnl,
        String reason,
        Instant timestamp
) {

    /**
     * Possible gateway actions.
     */
    public enum ExecutionAction {
        ENTER_LONG,
        EXIT_LONG,
        ENTER_SHORT,
        EXIT_SHORT,
        NOOP
    }

    /**
     * Convenience factory for no-op outcomes (HOLD, redundant signal, etc.).
     */
    public static ExecutionResult noop(String symbol, String reason) {
        return new ExecutionResult(
                ExecutionAction.NOOP, true, null, symbol,
                0, 0, 0, reason, Instant.now());
    }

    /**
     * Convenience factory for a successful fill.
     */
    public static ExecutionResult filled(ExecutionAction action, String symbol,
                                          String exchangeOrderId,
                                          double fillPrice, double fillQuantity,
                                          double realizedPnl, String reason) {
        return new ExecutionResult(
                action, true, exchangeOrderId, symbol,
                fillPrice, fillQuantity, realizedPnl, reason, Instant.now());
    }

    /**
     * Convenience factory for a failed execution attempt.
     */
    public static ExecutionResult failed(ExecutionAction action, String symbol, String reason) {
        return new ExecutionResult(
                action, false, null, symbol,
                0, 0, 0, reason, Instant.now());
    }
}
