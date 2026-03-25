package tradingbot.agent.domain.indicator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * BarSeriesIndicator — DIP port for ta4j-based streaming indicators.
 *
 * <p>Unlike the older {@link tradingbot.bot.strategy.indicator.TechnicalIndicator}
 * (which rebuilds a {@code BarSeries} from a full {@code List<Candle>} on every
 * call), this interface works with the <em>shared, incrementally-updated</em>
 * {@link BarSeries} that a {@link tradingbot.agent.ReactiveTradingAgent} maintains.
 *
 * <h3>Why a new interface?</h3>
 * <ul>
 *   <li><b>DIP</b>: {@code LLMTradingAgent} depends on this abstraction, not on
 *       concrete ta4j indicator classes.</li>
 *   <li><b>ISP</b>: callers only see {@link #indicator()} — no
 *       {@code compute(List<Candle>)} leaking the HTTP layer's data model.</li>
 *   <li><b>OCP</b>: new indicator implementations (Stochastic, ATR, …) can be
 *       added without touching any agent code.</li>
 * </ul>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Implementations must be <em>stateless</em> aside from the indicator
 *       reference they return — the {@link BarSeries} is owned by the agent.</li>
 *   <li>{@link #indicator()} is called once during agent initialization and the
 *       returned object is reused across all subsequent bar events.</li>
 *   <li>The factory method {@link #of(Indicator)} provides a trivial default
 *       implementation for wiring concrete ta4j indicators at configuration time.</li>
 * </ul>
 */
public interface BarSeriesIndicator {

    /**
     * Returns the underlying ta4j {@link Indicator} that will be evaluated
     * against the agent's {@link BarSeries}.
     *
     * @return a non-null {@link Indicator} operating on {@link Num} values
     */
    Indicator<Num> indicator();

    /**
     * Human-readable name used in logging and prompt templates
     * (e.g. {@code "MACD(12,26)"}, {@code "RSI(14)"}).
     */
    String name();

    /**
     * Convenience factory — wraps an existing ta4j {@link Indicator}.
     *
     * @param indicatorName  display name (used in logs / prompts)
     * @param ta4jIndicator  concrete ta4j indicator to wrap
     * @return a lightweight {@link BarSeriesIndicator} delegate
     */
    static BarSeriesIndicator of(String indicatorName, Indicator<Num> ta4jIndicator) {
        return new BarSeriesIndicator() {
            @Override public Indicator<Num> indicator() { return ta4jIndicator; }
            @Override public String name()              { return indicatorName; }
        };
    }
}
