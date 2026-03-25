package tradingbot.bot.service.backtest;

import java.util.List;

import tradingbot.bot.service.backtest.BacktestAgentExecutionService.TradeEvent;

/**
 * BacktestMetricsCalculator — DIP port for backtest statistics.
 *
 * <p>Isolates all financial-math from {@code BacktestService} (SRP) and lets
 * the implementation be swapped without touching the orchestration layer (OCP).
 *
 * <h3>Metrics produced</h3>
 * <ul>
 *   <li>Sharpe Ratio (annualised, risk-free rate = 0)</li>
 *   <li>Maximum Drawdown (peak-to-trough, expressed as a positive percentage)</li>
 *   <li>Win Rate (winning trades / total closed trades)</li>
 *   <li>Profit Factor (gross profit / gross loss)</li>
 *   <li>Equity Curve passed through unchanged for charting</li>
 * </ul>
 */
public interface BacktestMetricsCalculator {

    /**
     * Derives financial statistics from a completed
     * {@link BacktestAgentExecutionService.ExecutionResult}.
     *
     * @param result     raw execution data produced by the simulation loop
     * @param initialCapital the starting balance used in the simulation
     * @return a non-null {@link BacktestMetrics} snapshot
     */
    BacktestMetrics calculate(BacktestAgentExecutionService.ExecutionResult result,
                              double initialCapital);

    /**
     * Immutable snapshot of a completed backtest's financial statistics.
     *
     * @param runId            UUID assigned at calculation time — used to retrieve this run via REST
     * @param finalBalance     ending account balance
     * @param totalProfit      absolute profit/loss (finalBalance − initialCapital)
     * @param totalTrades      number of closed round-trip trades
     * @param winRate          winning trades / total trades  (0.0–1.0); NaN if no trades
     * @param profitFactor     gross profit / gross loss; {@code Double.MAX_VALUE} when no losses
     * @param maxDrawdownPct   maximum peak-to-trough decline as a positive percentage (0–100)
     * @param sharpeRatio      annualised Sharpe Ratio (risk-free rate = 0); NaN if < 2 bars
     * @param equityCurve      typed equity samples — one {@link EquityCurvePoint} per bar
     * @param trades           full per-trade audit log for CSV/JSON export
     */
    record BacktestMetrics(
            String runId,
            double finalBalance,
            double totalProfit,
            int totalTrades,
            double winRate,
            double profitFactor,
            double maxDrawdownPct,
            double sharpeRatio,
            List<EquityCurvePoint> equityCurve,
            List<TradeEvent> trades) {

        /** Returns true if the backtest was profitable. */
        public boolean isProfitable() { return totalProfit > 0; }

        @Override
        public String toString() {
            return ("BacktestMetrics{runId=%s, finalBalance=%.2f, profit=%.2f, trades=%d, " +
                    "winRate=%.1f%%, profitFactor=%.2f, maxDD=%.1f%%, sharpe=%.3f}")
                    .formatted(runId, finalBalance, totalProfit, totalTrades,
                               winRate * 100, profitFactor, maxDrawdownPct, sharpeRatio);
        }
    }
}
