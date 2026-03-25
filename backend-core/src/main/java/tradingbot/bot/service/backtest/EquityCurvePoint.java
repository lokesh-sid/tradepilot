package tradingbot.bot.service.backtest;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * EquityCurvePoint — a single sample on the equity curve.
 *
 * <p>Replaces the previously untyped {@code List<Double>} equity curve in
 * {@link BacktestMetricsCalculator.BacktestMetrics}. Each point now carries
 * enough information to:
 * <ul>
 *   <li>Plot balance over time (timestamp as x-axis)</li>
 *   <li>Overlay drawdown on the same chart (drawdownPct y-axis)</li>
 *   <li>Mark trade entry/exit events (action)</li>
 *   <li>Filter by symbol in multi-symbol backtests (Phase 3)</li>
 * </ul>
 *
 * @param barIndex    zero-based position of this bar in the replay history
 * @param timestamp   closing time of the bar (Instant, UTC)
 * @param balance     running portfolio balance after this bar ({@code BigDecimal} for precision)
 * @param drawdownPct current drawdown percentage from the running equity peak (0–100, positive)
 * @param action      the agent action taken at this bar: {@code "BUY"}, {@code "SELL"}, or {@code "HOLD"}
 * @param symbol      trading pair symbol (e.g., {@code "BTCUSDT"})
 */
public record EquityCurvePoint(
        int barIndex,
        Instant timestamp,
        BigDecimal balance,
        double drawdownPct,
        String action,
        String symbol) {

    /** Returns {@code true} when the balance at this point is above the initial capital. */
    public boolean isProfit(double initialCapital) {
        return balance.doubleValue() > initialCapital;
    }
}
