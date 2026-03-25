package tradingbot.bot.service.backtest;

import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;

import org.springframework.stereotype.Service;

import tradingbot.agent.ReactiveTradingAgent;
import tradingbot.agent.TradingAgentFactory;
import tradingbot.bot.controller.exception.BotOperationException;
import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.bot.service.backtest.BacktestMetricsCalculator.BacktestMetrics;
import tradingbot.config.TradingConfig;

/**
 * BacktestService — orchestrates the full backtest pipeline.
 *
 * <h3>Responsibility (SRP)</h3>
 * This class is a thin orchestrator: <em>load → create agent → execute → metrics</em>.
 * All algorithmic work lives in dedicated collaborators:
 * <ul>
 *   <li>{@link TradingAgentFactory} — constructs + starts the agent under test.</li>
 *   <li>{@link BacktestAgentExecutionService} — drives the candle-by-candle replay loop.</li>
 *   <li>{@link BacktestMetricsCalculator} — computes Sharpe, drawdown, win rate, etc.</li>
 * </ul>
 *
 * <h3>DIP alignment</h3>
 * All three collaborators are interfaces. Spring wires the active implementations
 * ({@code LLMTradingAgentFactory}, {@code CsvBacktestAgentExecutionService},
 * {@code StandardBacktestMetricsCalculator}) — this class knows nothing about concrete types.
 *
 * <h3>What was removed vs. the original</h3>
 * <ul>
 *   <li>{@code new FuturesTradingBot(...)} — delegated to {@link TradingAgentFactory}.</li>
 *   <li>{@code MockSentimentAnalyzer} inner class — deleted; {@code CachedGrokService} handles it.</li>
 *   <li>{@code TradeDirection.LONG} hardcode — removed; direction is agent-driven.</li>
 *   <li>Inline indicator instantiation ({@code new RSITechnicalIndicator(14)}, etc.) — inside agent.</li>
 *   <li>{@code BacktestResult} (3-field class) → {@link BacktestMetrics} record (8 fields).</li>
 * </ul>
 */
@Service
public class BacktestService {

    private static final Logger LOGGER = Logger.getLogger(BacktestService.class.getName());

    /** Starting capital for all backtests. Phase 3: derive from TradingConfig. */
    private static final double INITIAL_CAPITAL = 10_000.0;

    private final HistoricalDataLoader dataLoader;
    private final TradingAgentFactory agentFactory;
    private final BacktestAgentExecutionService executionService;
    private final BacktestMetricsCalculator metricsCalculator;
    private final BacktestRunRegistry runRegistry;

    public BacktestService(HistoricalDataLoader dataLoader,
                           TradingAgentFactory agentFactory,
                           BacktestAgentExecutionService executionService,
                           BacktestMetricsCalculator metricsCalculator,
                           BacktestRunRegistry runRegistry) {
        this.dataLoader        = dataLoader;
        this.agentFactory      = agentFactory;
        this.executionService  = executionService;
        this.metricsCalculator = metricsCalculator;
        this.runRegistry       = runRegistry;
    }

    // ── public API ─────────────────────────────────────────────────────────────

    /**
     * Runs a backtest using CSV data from an {@link InputStream}.
     * Used by {@code BacktestController} (multipart REST upload).
     */
    public BacktestMetrics runBacktest(InputStream csvData, TradingConfig config,
                                       long latencyMs, double slippagePercent,
                                       double feeRate) {
        LOGGER.info("Starting backtest (stream) for " + config.getSymbol());
        List<Candle> history = dataLoader.loadFromStream(csvData);
        if (history.isEmpty()) {
            throw new BotOperationException("backtest", "No data loaded from stream");
        }
        return executeBacktest(history, config, latencyMs, slippagePercent, feeRate);
    }

    /**
     * Runs a backtest using a CSV file path.
     * Used in integration tests and CLI tooling.
     */
    public BacktestMetrics runBacktest(String csvFilePath, TradingConfig config,
                                       long latencyMs, double slippagePercent,
                                       double feeRate) {
        LOGGER.info("Starting backtest (file) for " + config.getSymbol());
        List<Candle> history = dataLoader.loadFromCsv(csvFilePath);
        if (history.isEmpty()) {
            throw new BotOperationException("backtest", "No data loaded from " + csvFilePath);
        }
        return executeBacktest(history, config, latencyMs, slippagePercent, feeRate);
    }

    // ── private pipeline ───────────────────────────────────────────────────────

    private BacktestMetrics executeBacktest(List<Candle> history, TradingConfig config,
                                            long latencyMs, double slippagePercent,
                                            double feeRate) {
        // 1. Simulation exchange (concrete — this is a value object not a service boundary)
        BacktestExchangeService exchange =
                new BacktestExchangeService(latencyMs, slippagePercent, feeRate);

        // 2. Create + start agent via factory (DIP — no FuturesTradingBot instantiation here)
        ReactiveTradingAgent agent = agentFactory.create(config);
        LOGGER.info("Agent created: " + agent.getId() + " via " + agentFactory.describe());

        try {
            // 3. Replay loop (SRP — all candle iteration in executionService)
            BacktestAgentExecutionService.ExecutionResult rawResult =
                    executionService.execute(agent, history, config, exchange);

            // 4. Derive financial statistics (SRP — all math in metricsCalculator)
            BacktestMetrics metrics = metricsCalculator.calculate(rawResult, INITIAL_CAPITAL);
            LOGGER.info("Backtest completed: " + metrics);

            // 5. Persist run so it can be retrieved via GET /api/v1/backtest/{runId}
            runRegistry.save(metrics);
            return metrics;

        } finally {
            agent.stop(); // release ta4j / scheduler resources
        }
    }
}
