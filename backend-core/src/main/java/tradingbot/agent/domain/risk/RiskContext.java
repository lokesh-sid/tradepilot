package tradingbot.agent.domain.risk;

/**
 * RiskContext — immutable snapshot of a position's risk state, passed
 * to {@link RiskGuard#evaluate} on every candle close.
 *
 * <p>This deliberately does <em>not</em> reference domain entities
 * ({@code Position}, {@code Order}) so that it can be constructed in
 * both live and backtest environments without JPA dependencies.
 *
 * @param agentId          owning agent identifier
 * @param symbol           trading pair (e.g. "BTCUSDT")
 * @param hasOpenPosition  true if the agent currently holds a position
 * @param positionSide     "LONG" or "SHORT" (null if no open position)
 * @param entryPrice       average entry price (0 if no open position)
 * @param quantity         position size (0 if no open position)
 * @param stopLossPrice    hard stop-loss price (null if not configured)
 * @param takeProfitPrice  hard take-profit price (null if not configured)
 * @param maxLossPercent   maximum allowed loss as a percentage (e.g. 2.0 for 2%)
 * @param maxGainPercent   maximum allowed gain as a percentage (e.g. 5.0 for 5%)
 */
public record RiskContext(
        String agentId,
        String symbol,
        boolean hasOpenPosition,
        String positionSide,
        double entryPrice,
        double quantity,
        Double stopLossPrice,
        Double takeProfitPrice,
        double maxLossPercent,
        double maxGainPercent
) {
    /**
     * Creates an empty context for agents with no open position.
     */
    public static RiskContext noPosition(String agentId, String symbol) {
        return new RiskContext(agentId, symbol, false, null, 0, 0,
                null, null, 0, 0);
    }

    /**
     * Creates a context for an active long position.
     */
    public static RiskContext longPosition(String agentId, String symbol,
                                           double entryPrice, double quantity,
                                           Double stopLossPrice, Double takeProfitPrice,
                                           double maxLossPercent, double maxGainPercent) {
        return new RiskContext(agentId, symbol, true, "LONG",
                entryPrice, quantity, stopLossPrice, takeProfitPrice,
                maxLossPercent, maxGainPercent);
    }

    /**
     * Creates a context for an active short position.
     */
    public static RiskContext shortPosition(String agentId, String symbol,
                                            double entryPrice, double quantity,
                                            Double stopLossPrice, Double takeProfitPrice,
                                            double maxLossPercent, double maxGainPercent) {
        return new RiskContext(agentId, symbol, true, "SHORT",
                entryPrice, quantity, stopLossPrice, takeProfitPrice,
                maxLossPercent, maxGainPercent);
    }
}
