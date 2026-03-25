package tradingbot.bot.service.backtest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tradingbot.agent.ReactiveTradingAgent;
import tradingbot.agent.domain.model.AgentDecision;
import tradingbot.agent.impl.execution.BacktestOrderGateway;
import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.config.TradingConfig;
import tradingbot.domain.market.KlineClosedEvent;

/**
 * CsvBacktestAgentExecutionService — drives an {@link ReactiveTradingAgent}
 * through a list of historical {@link Candle}s and records simulated fills.
 *
 * <h3>Replay loop</h3>
 * <ol>
 *   <li>Call {@link BacktestExchangeService#setMarketContext(List, int)} for each bar
 *       (sets current price + processes pending orders internally).</li>
 *   <li>Convert the bar to a {@link KlineClosedEvent} and call
 *       {@code agent.onKlineClosed(event).block()} — LLM reasoning is exercised
 *       for every bar (CachedGrokService returns synthetic / cached response
 *       instantaneously in backtest mode).</li>
 *   <li>Route BUY/SELL decisions through the exchange; HOLD is a no-op.</li>
 *   <li>Record the running equity after each bar and every fill as a
 *       {@link tradingbot.bot.service.backtest.BacktestAgentExecutionService.TradeEvent}.</li>
 * </ol>
 *
 * <h3>Position model</h3>
 * Directional tracking delegated to {@link BacktestOrderGateway} (P1).
 * <ul>
 *   <li>BUY signal → enter long / close short (via gateway)</li>
 *   <li>SELL signal → enter short / close long (via gateway)</li>
 *   <li>HOLD → no-op</li>
 *   <li>Quantity = fixed 1.0 contract (configurable per agent in Phase 3)</li>
 * </ul>
 */
@Component
public class CsvBacktestAgentExecutionService implements BacktestAgentExecutionService {

    private static final Logger log =
            LoggerFactory.getLogger(CsvBacktestAgentExecutionService.class);

    @Override
    public ExecutionResult execute(ReactiveTradingAgent agent,
                                   List<Candle> history,
                                   TradingConfig config,
                                   BacktestExchangeService exchange) {

        String symbol    = config.getSymbol();
        int    totalBars = history.size();

        List<TradeEvent>       trades      = new ArrayList<>();
        List<EquityCurvePoint> equityCurve = new ArrayList<>(totalBars);

        // P1: Use BacktestOrderGateway instead of inline position tracking
        BacktestOrderGateway gateway = new BacktestOrderGateway(exchange, null);

        log.info("[CsvBacktest] starting replay: symbol={} bars={} agent={}",
                symbol, totalBars, agent.getId());

        for (int i = 0; i < totalBars; i++) {
            Candle candle = history.get(i);

            // 1. Advance time: sets current price + processes pending fills
            exchange.setMarketContext(history, i);

            // 2. Ask the agent to evaluate this closed bar
            KlineClosedEvent event = toKlineEvent(symbol, candle, config);
            AgentDecision decision;
            try {
                decision = agent.onKlineClosed(event).block();
            } catch (Exception ex) {
                log.warn("[CsvBacktest] bar {} agent error: {}", i, ex.getMessage());
                equityCurve.add(equityPoint(i, candle, exchange, "HOLD", symbol));
                continue;
            }

            if (decision == null) {
                equityCurve.add(equityPoint(i, candle, exchange, "HOLD", symbol));
                continue;
            }

            // 3. Route decision through the gateway
            double currentPrice = event.close().doubleValue();
            tradingbot.agent.domain.execution.ExecutionResult gwResult =
                    gateway.execute(decision, symbol, currentPrice);

            String barAction = decision.action().name();

            if (gwResult.success() && gwResult.action() != tradingbot.agent.domain.execution.ExecutionResult.ExecutionAction.NOOP) {
                barAction = switch (gwResult.action()) {
                    case ENTER_LONG, EXIT_SHORT -> "BUY";
                    case EXIT_LONG, ENTER_SHORT -> "SELL";
                    default -> barAction;
                };
                trades.add(new TradeEvent(i, symbol, barAction, gwResult.fillPrice(),
                        gwResult.fillQuantity(), gwResult.realizedPnl(), decision.reasoning()));
                log.debug("[CsvBacktest] bar={} {} @ {} pnl={}", i, barAction,
                        gwResult.fillPrice(), gwResult.realizedPnl());
            }

            // 4. Record equity snapshot after the bar (drawdownPct filled in by StandardBacktestMetricsCalculator)
            equityCurve.add(equityPoint(i, candle, exchange, barAction, symbol));
        }

        log.info("[CsvBacktest] replay complete: bars={} trades={} finalBalance={}",
                totalBars, trades.size(), exchange.getMarginBalance());

        return new ExecutionResult(trades, equityCurve, totalBars);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * Builds an {@link EquityCurvePoint} for the current bar.
     * {@code drawdownPct} is left at {@code 0.0}; the running-peak drawdown is
     * calculated in a second pass inside {@link StandardBacktestMetricsCalculator}.
     */
    private EquityCurvePoint equityPoint(int barIndex, Candle candle,
                                          BacktestExchangeService exchange,
                                          String action, String symbol) {
        return new EquityCurvePoint(
                barIndex,
                Instant.ofEpochMilli(candle.getCloseTime()),
                java.math.BigDecimal.valueOf(exchange.getMarginBalance()),
                0.0,
                action,
                symbol);
    }

    /**
     * Converts a {@link Candle} (epoch-ms timestamps) into a
     * {@link KlineClosedEvent} (Instant).
     */
    private KlineClosedEvent toKlineEvent(String symbol, Candle candle, TradingConfig config) {
        String exchange = "BACKTEST";
        String interval = String.valueOf(config.getInterval()) + "m";
        Instant openTime  = Instant.ofEpochMilli(candle.getOpenTime());
        Instant closeTime = Instant.ofEpochMilli(candle.getCloseTime());

        // Guard against null OHLCV (malformed CSV rows)
        BigDecimal open   = nvl(candle.getOpen());
        BigDecimal high   = nvl(candle.getHigh());
        BigDecimal low    = nvl(candle.getLow());
        BigDecimal close  = nvl(candle.getClose());
        BigDecimal volume = nvl(candle.getVolume());

        return new KlineClosedEvent(exchange, symbol, interval,
                open, high, low, close, volume, openTime, closeTime);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
