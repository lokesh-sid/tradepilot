package tradingbot.bot.service.backtest;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Service;

import tradingbot.bot.service.backtest.BacktestAgentExecutionService.TradeEvent;

/**
 * EquityCurveExportService — serializes backtest results to JSON-friendly
 * structures and CSV byte arrays.
 *
 * <p>No new dependency is required: CSV is built manually as strings.
 * Jackson (already present) handles the JSON serialization via Spring MVC's
 * {@code @RestController} return value handling.
 *
 * <h3>CSV formats</h3>
 *
 * <b>equity-curve.csv:</b>
 * <pre>
 * barIndex,timestamp,balance,drawdownPct,action,symbol
 * 0,2024-01-01T00:00:00Z,10000.00,0.00,HOLD,BTCUSDT
 * ...
 * </pre>
 *
 * <b>trades.csv:</b>
 * <pre>
 * barIndex,symbol,side,price,quantity,pnl,reasoning
 * 12,BTCUSDT,BUY,42500.00,1.0,0.00,"MACD bullish crossover..."
 * ...
 * </pre>
 */
@Service
public class EquityCurveExportService {

    private static final String EQUITY_CURVE_HEADER =
            "barIndex,timestamp,balance,drawdownPct,action,symbol\n";

    private static final String TRADES_HEADER =
            "barIndex,symbol,side,price,quantity,pnl,reasoning\n";

    // ── equity curve ─────────────────────────────────────────────────────────

    /**
     * Serializes an equity curve to CSV bytes.
     *
     * @param points the equity curve from {@link BacktestMetricsCalculator.BacktestMetrics#equityCurve()}
     * @return UTF-8 encoded CSV bytes ready for an HTTP response body
     */
    public byte[] equityCurveToCsv(List<EquityCurvePoint> points) {
        StringBuilder sb = new StringBuilder(points.size() * 80);
        sb.append(EQUITY_CURVE_HEADER);

        for (EquityCurvePoint p : points) {
            sb.append(p.barIndex()).append(',');
            sb.append(p.timestamp()).append(',');
            sb.append(String.format("%.2f", p.balance().doubleValue())).append(',');
            sb.append(String.format("%.4f", p.drawdownPct())).append(',');
            sb.append(escapeCsv(p.action())).append(',');
            sb.append(escapeCsv(p.symbol())).append('\n');
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── trades ────────────────────────────────────────────────────────────────

    /**
     * Serializes a trade list to CSV bytes.
     *
     * @param trades the trade list from {@link BacktestMetricsCalculator.BacktestMetrics#trades()}
     * @return UTF-8 encoded CSV bytes ready for an HTTP response body
     */
    public byte[] tradesToCsv(List<TradeEvent> trades) {
        StringBuilder sb = new StringBuilder(trades.size() * 120);
        sb.append(TRADES_HEADER);

        for (TradeEvent t : trades) {
            sb.append(t.barIndex()).append(',');
            sb.append(escapeCsv(t.symbol())).append(',');
            sb.append(escapeCsv(t.side())).append(',');
            sb.append(String.format("%.2f", t.price())).append(',');
            sb.append(String.format("%.6f", t.quantity())).append(',');
            sb.append(String.format("%.2f", t.pnl())).append(',');
            sb.append(escapeCsv(t.reasoning())).append('\n');
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Wraps a cell value in double-quotes and escapes any internal double-quotes
     * per RFC 4180. Returns an empty quoted string for null input.
     */
    private String escapeCsv(String value) {
        if (value == null) return "\"\"";
        // Wrap in quotes; escape internal quotes by doubling them
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
