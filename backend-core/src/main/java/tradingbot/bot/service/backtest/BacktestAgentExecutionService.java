package tradingbot.bot.service.backtest;

import java.util.List;

import tradingbot.agent.ReactiveTradingAgent;
import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.config.TradingConfig;

/**
 * BacktestAgentExecutionService — DIP port for the backtest simulation loop.
 *
 * <p>Extracts the inline simulation loop from {@code BacktestService.executeBacktest()}
 * into a dedicated abstraction.  This resolves two SOLID violations:
 *
 * <ul>
 *   <li><b>SRP</b>: {@code BacktestService} no longer owns the loop logic; it only
 *       orchestrates the sequence (load → exec → metrics).</li>
 *   <li><b>DIP</b>: {@code BacktestService} depends on this interface, not on
 *       {@link tradingbot.bot.FuturesTradingBot} or any concrete simulator.</li>
 * </ul>
 *
 * <h3>Replay contract</h3>
 * Implementations must:
 * <ol>
 *   <li>Iterate historical bars in chronological order.</li>
 *   <li>For each bar, build a {@link tradingbot.domain.market.KlineClosedEvent}
 *       and call {@code agent.onKlineClosed(event).block()} so that LLM reasoning
 *       is exercised for every candle.</li>
 *   <li>Route any BUY/SELL {@link tradingbot.agent.domain.model.AgentDecision}
 *       through the provided {@link BacktestExchangeService} for simulated fill.</li>
 *   <li>Record the running equity value after each bar close.</li>
 * </ol>
 */
public interface BacktestAgentExecutionService {

    /**
     * Runs a full replay of {@code history} through {@code agent} and returns
     * raw execution data ready for metrics calculation.
     *
     * @param agent    fully initialised {@link ReactiveTradingAgent} in
     *                 {@code ACTIVE} state
     * @param history  historical OHLCV bars in chronological order
     * @param config   trading config for the run (symbol, leverage, capital…)
     * @param exchange the backtest exchange to route decisions through
     * @return a non-null {@link ExecutionResult} containing trades + equity curve
     */
    ExecutionResult execute(ReactiveTradingAgent agent,
                            List<Candle> history,
                            TradingConfig config,
                            BacktestExchangeService exchange);

    /**
     * Raw output of a single backtest replay.
     *
     * @param trades          ordered list of simulated trade events
     * @param equityCurve     equity snapshot after every bar — typed {@link EquityCurvePoint}
     *                        carrying timestamp, balance, action and symbol per bar
     * @param barsProcessed   total number of historical bars replayed
     */
    record ExecutionResult(
            List<TradeEvent> trades,
            List<EquityCurvePoint> equityCurve,
            int barsProcessed) {

        /** Returns true when at least one trade was executed. */
        public boolean hasActivity() { return !trades.isEmpty(); }
    }

    /**
     * A single simulated trade that was filled during the replay.
     *
     * @param barIndex   position in the history list when the fill occurred
     * @param symbol     trading pair
     * @param side       {@code "BUY"} or {@code "SELL"}
     * @param price      fill price (after slippage)
     * @param quantity   base-asset quantity filled
     * @param pnl        realized PnL at fill time (0.0 for opening trades)
     * @param reasoning  LLM reasoning text that triggered this trade
     */
    record TradeEvent(
            int barIndex,
            String symbol,
            String side,
            double price,
            double quantity,
            double pnl,
            String reasoning) {}
}
