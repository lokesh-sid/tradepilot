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
 * @param entryPrice      price at which the position was originally opened (0 for entries and NOOP)
 * @param realizedPnl     absolute PnL from closing a position in the account's quote currency (0 for entries and NOOP)
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
        double entryPrice,
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
                0, 0, 0, 0, reason, Instant.now());
    }

    /**
     * Convenience factory for a successful entry fill (no prior position to close).
     */
    public static ExecutionResult filled(ExecutionAction action, String symbol,
                                          String exchangeOrderId,
                                          double fillPrice, double fillQuantity,
                                          double realizedPnl, String reason) {
        return new ExecutionResult(
                action, true, exchangeOrderId, symbol,
                fillPrice, fillQuantity, 0, realizedPnl, reason, Instant.now());
    }

    /**
     * Convenience factory for a successful exit fill.
     * Carries the original {@code entryPrice} so that callers can compute
     * an accurate PnL percentage without a back-calculation approximation.
     */
    public static ExecutionResult filledExit(ExecutionAction action, String symbol,
                                              String exchangeOrderId,
                                              double fillPrice, double fillQuantity,
                                              double entryPrice, double realizedPnl, String reason) {
        return new ExecutionResult(
                action, true, exchangeOrderId, symbol,
                fillPrice, fillQuantity, entryPrice, realizedPnl, reason, Instant.now());
    }

    /**
     * Convenience factory for a failed execution attempt.
     */
    public static ExecutionResult failed(ExecutionAction action, String symbol, String reason) {
        return new ExecutionResult(
                action, false, null, symbol,
                0, 0, 0, 0, reason, Instant.now());
    }
}
