package tradingbot.bot.service.backtest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import tradingbot.bot.service.backtest.BacktestAgentExecutionService.TradeEvent;

/**
 * StandardBacktestMetricsCalculator — default {@link BacktestMetricsCalculator}.
 *
 * <p>Implements every metric from scratch using only the
 * {@link BacktestAgentExecutionService.ExecutionResult} — no external
 * dependencies required.
 *
 * <h3>Metrics</h3>
 * <ul>
 *   <li><b>Sharpe Ratio</b> — per-bar return mean / per-bar return stddev.
 *       {@code NaN} when fewer than two equity samples exist.</li>
 *   <li><b>Max Drawdown %</b> — maximum observed peak-to-trough decline,
 *       expressed as a positive percentage (0–100).</li>
 *   <li><b>Win Rate</b> — fraction of closed (SELL) trades with positive PnL.</li>
 *   <li><b>Profit Factor</b> — gross profit / gross loss.
 *       {@code Double.MAX_VALUE} when there are no losing trades.</li>
 * </ul>
 *
 * <h3>Edge cases</h3>
 * <ul>
 *   <li>Zero trades → win rate = {@code Double.NaN}, profit factor = {@code 1.0}.</li>
 *   <li>Single equity sample → Sharpe = {@code Double.NaN}.</li>
 *   <li>Flat equity curve → Sharpe = {@code 0.0} (zero stddev guard).</li>
 * </ul>
 */
@Component
public class StandardBacktestMetricsCalculator implements BacktestMetricsCalculator {

    @Override
    public BacktestMetrics calculate(BacktestAgentExecutionService.ExecutionResult result,
                                     double initialCapital) {

        List<EquityCurvePoint> rawPoints = result.equityCurve();
        List<TradeEvent>       trades    = result.trades();

        // Extract raw balance doubles for statistical calculations
        List<Double> balances = rawPoints.stream()
                .map(p -> p.balance().doubleValue())
                .toList();

        double finalBalance = balances.isEmpty() ? initialCapital : balances.get(balances.size() - 1);
        double totalProfit  = finalBalance - initialCapital;

        // ── Win Rate + Profit Factor ──────────────────────────────────────────
        int closedTrades = 0;
        int winCount     = 0;
        double grossProfit = 0.0;
        double grossLoss   = 0.0;

        for (var trade : trades) {
            if ("SELL".equals(trade.side())) {      // only closed (exit) trades
                closedTrades++;
                if (trade.pnl() > 0) {
                    winCount++;
                    grossProfit += trade.pnl();
                } else if (trade.pnl() < 0) {
                    grossLoss += Math.abs(trade.pnl());
                }
            }
        }

        double winRate      = closedTrades == 0 ? Double.NaN : (double) winCount / closedTrades;
        double profitFactor = grossLoss == 0.0
                ? (grossProfit > 0 ? Double.MAX_VALUE : 1.0)
                : grossProfit / grossLoss;

        // ── Max Drawdown (global peak-to-trough) ─────────────────────────────
        double maxDrawdownPct = 0.0;
        if (!balances.isEmpty()) {
            double peak = balances.get(0);
            for (double val : balances) {
                if (val > peak) {
                    peak = val;
                } else if (peak > 0) {
                    double dd = (peak - val) / peak * 100.0;
                    if (dd > maxDrawdownPct) {
                        maxDrawdownPct = dd;
                    }
                }
            }
        }

        // ── Sharpe Ratio (per-bar, raw) ───────────────────────────────────────
        double sharpeRatio = computeSharpe(balances);

        // ── Enrich equity curve with per-point drawdown (second pass) ─────────
        List<EquityCurvePoint> equityCurveWithDrawdown = computeEquityCurveWithDrawdown(rawPoints, initialCapital);

        return new BacktestMetrics(
                UUID.randomUUID().toString(),
                finalBalance,
                totalProfit,
                closedTrades,
                winRate,
                profitFactor,
                maxDrawdownPct,
                sharpeRatio,
                equityCurveWithDrawdown,
                trades);
    }

    // ── private helpers ────────────────────────────────────────────────────────

    /**     * Enriches raw {@link EquityCurvePoint}s (which have {@code drawdownPct=0.0})
     * with the running peak-to-trough drawdown at each point.
     */
    private List<EquityCurvePoint> computeEquityCurveWithDrawdown(
            List<EquityCurvePoint> rawPoints, double initialCapital) {

        List<EquityCurvePoint> result = new ArrayList<>(rawPoints.size());
        double peak = initialCapital;

        for (EquityCurvePoint p : rawPoints) {
            double bal = p.balance().doubleValue();
            if (bal > peak) {
                peak = bal;
            }
            double dd = peak > 0 ? (peak - bal) / peak * 100.0 : 0.0;
            result.add(new EquityCurvePoint(
                    p.barIndex(),
                    p.timestamp(),
                    p.balance(),
                    dd,
                    p.action(),
                    p.symbol()));
        }
        return result;
    }

    /**     * Computes the raw (per-bar) Sharpe Ratio from an equity curve.
     * Returns {@code Double.NaN} for fewer than 2 samples.
     * Returns {@code 0.0} when all returns are identical (zero std-dev).
     */
    private double computeSharpe(List<Double> equity) {
        if (equity.size() < 2) {
            return Double.NaN;
        }

        double[] returns = new double[equity.size() - 1];
        for (int i = 1; i < equity.size(); i++) {
            double prev = equity.get(i - 1);
            double curr = equity.get(i);
            returns[i - 1] = prev == 0 ? 0 : (curr - prev) / prev;
        }

        double mean = 0.0;
        for (double r : returns) mean += r;
        mean /= returns.length;

        double variance = 0.0;
        for (double r : returns) variance += (r - mean) * (r - mean);
        variance /= returns.length;

        double stdDev = Math.sqrt(variance);
        if (stdDev == 0.0) return 0.0;

        return mean / stdDev;
    }
}
