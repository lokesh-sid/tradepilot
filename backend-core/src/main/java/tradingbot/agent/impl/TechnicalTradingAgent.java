package tradingbot.agent.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNum;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tradingbot.agent.ReactiveTradingAgent;
import tradingbot.agent.domain.model.AgentDecision;
import tradingbot.agent.domain.model.AgentDecision.Action;
import tradingbot.agent.domain.model.AgentStatus;
import tradingbot.agent.domain.risk.RiskContext;
import tradingbot.agent.domain.risk.RiskGuard;
import tradingbot.domain.market.KlineClosedEvent;

/**
 * TechnicalTradingAgent — pure technical analysis implementation of
 * {@link ReactiveTradingAgent} with no LLM dependency.
 *
 * <p>Uses MACD + RSI + Bollinger Bands to generate entry signals,
 * mirroring the logic from {@code FuturesTradingBot} but wired into the
 * reactive {@link ReactiveTradingAgent} contract so it integrates with
 * {@code AgentOrchestrator}'s event-driven dispatch path.
 *
 * <h3>Signal logic</h3>
 * <ul>
 *   <li><b>BUY (LONG entry)</b>: MACD bullish crossover (histogram crosses above
 *       zero) AND RSI ≤ oversold threshold AND close ≤ bbLower × 1.01</li>
 *   <li><b>SELL (SHORT entry / long exit)</b>: MACD bearish crossover AND
 *       RSI ≥ overbought threshold AND close ≥ bbUpper × 0.99</li>
 *   <li><b>HOLD</b>: all other conditions</li>
 * </ul>
 *
 * <p>A {@link RiskGuard} check runs before indicator evaluation. If the
 * guard fires (hard SL/TP hit), it short-circuits to SELL immediately.
 *
 * <p>Instances are created by {@code AgentFactory} per {@code AgentEntity}
 * and registered dynamically with {@code AgentOrchestrator}.
 */
public class TechnicalTradingAgent implements ReactiveTradingAgent {

    private static final Logger log = LoggerFactory.getLogger(TechnicalTradingAgent.class);

    // --- identity ---------------------------------------------------------------
    private final String agentId;
    private final String symbol;
    private final String exchange;

    // --- dependencies -----------------------------------------------------------
    private final RiskGuard riskGuard;
    private final Supplier<RiskContext> riskContextSupplier;

    // --- indicator config -------------------------------------------------------
    private final int macdFast;
    private final int macdSlow;
    private final int macdSignal;
    private final int rsiPeriod;
    private final double rsiOversold;
    private final double rsiOverbought;
    private final int bbPeriod;
    private final double bbStdDev;
    private final int warmupBars;

    // --- ta4j state (initialised in start()) ------------------------------------
    private BaseBarSeries barSeries;
    private ClosePriceIndicator closePrice;
    private MACDIndicator macdIndicator;
    private EMAIndicator signalLine;
    private RSIIndicator rsiIndicator;
    private BollingerBandsMiddleIndicator bbMiddle;
    private StandardDeviationIndicator bbStdDevIndicator;
    private BollingerBandsLowerIndicator bbLower;
    private BollingerBandsUpperIndicator bbUpper;

    // --- lifecycle state --------------------------------------------------------
    private final AtomicReference<AgentStatus> status =
            new AtomicReference<>(AgentStatus.CREATED);
    private final AtomicInteger iterationCount = new AtomicInteger(0);

    public TechnicalTradingAgent(String agentId, String symbol, String exchange,
                                 RiskGuard riskGuard,
                                 Supplier<RiskContext> riskContextSupplier,
                                 int macdFast, int macdSlow, int macdSignal,
                                 int rsiPeriod, double rsiOversold, double rsiOverbought,
                                 int bbPeriod, double bbStdDev) {
        this.agentId = agentId;
        this.symbol = symbol;
        this.exchange = exchange;
        this.riskGuard = riskGuard;
        this.riskContextSupplier = riskContextSupplier;
        this.macdFast = macdFast;
        this.macdSlow = macdSlow;
        this.macdSignal = macdSignal;
        this.rsiPeriod = rsiPeriod;
        this.rsiOversold = rsiOversold;
        this.rsiOverbought = rsiOverbought;
        this.bbPeriod = bbPeriod;
        this.bbStdDev = bbStdDev;
        this.warmupBars = Math.max(macdSlow + macdSignal, bbPeriod);
    }

    // ── TradingAgent lifecycle ─────────────────────────────────────────────────

    @Override
    public void start() {
        if (!status.compareAndSet(AgentStatus.CREATED, AgentStatus.INITIALIZING) &&
            !status.compareAndSet(AgentStatus.STOPPED, AgentStatus.INITIALIZING)) {
            log.warn("[{}] start() called in unexpected state: {}", agentId, status.get());
            return;
        }
        barSeries         = new BaseBarSeriesBuilder().withName(agentId + "-series").build();
        closePrice        = new ClosePriceIndicator(barSeries);
        macdIndicator     = new MACDIndicator(closePrice, macdFast, macdSlow);
        signalLine        = new EMAIndicator(macdIndicator, macdSignal);
        rsiIndicator      = new RSIIndicator(closePrice, rsiPeriod);
        bbMiddle          = new BollingerBandsMiddleIndicator(closePrice);
        bbStdDevIndicator = new StandardDeviationIndicator(closePrice, bbPeriod);
        bbLower           = new BollingerBandsLowerIndicator(bbMiddle, bbStdDevIndicator, DecimalNum.valueOf(bbStdDev));
        bbUpper           = new BollingerBandsUpperIndicator(bbMiddle, bbStdDevIndicator, DecimalNum.valueOf(bbStdDev));

        status.set(AgentStatus.ACTIVE);
        log.info("[{}] started — MACD({},{},{}) RSI({}) BB({},{}) warmup={} bars",
                agentId, macdFast, macdSlow, macdSignal, rsiPeriod, bbPeriod, bbStdDev, warmupBars);
    }

    @Override
    public void stop() {
        AgentStatus previous = status.getAndSet(AgentStatus.STOPPED);
        log.info("[{}] stopped (was {})", agentId, previous);
    }

    @Override
    public void pause() {
        status.compareAndSet(AgentStatus.ACTIVE, AgentStatus.PAUSED);
    }

    @Override
    public void resume() {
        status.compareAndSet(AgentStatus.PAUSED, AgentStatus.ACTIVE);
    }

    // ── ReactiveTradingAgent ─────────────────────────────────────────────────────

    @Override public String getSymbol()    { return symbol; }
    @Override public String getExchange()  { return exchange; }
    @Override public AgentStatus getStatus() { return status.get(); }

    @Override
    public Mono<AgentDecision> onKlineClosed(KlineClosedEvent event) {
        RiskParams params = getCurrentRiskParams();
        if (status.get() != AgentStatus.ACTIVE) {
            return Mono.just(AgentDecision.of(agentId, symbol, Action.HOLD, 0,
                    "Agent not ACTIVE (status=" + status.get() + ")",
                    params.quantity, params.stopLossPercent, params.takeProfitPercent));
        }
        return Mono.fromCallable(() -> evaluate(event, params))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.error("[{}] error evaluating bar at {}: {}", agentId, event.closeTime(), ex.getMessage());
                    status.compareAndSet(AgentStatus.ACTIVE, AgentStatus.ERROR);
                    return Mono.just(AgentDecision.of(agentId, symbol, Action.HOLD, 0,
                            "Evaluation error: " + ex.getMessage(),
                            params.quantity, params.stopLossPercent, params.takeProfitPercent));
                });
    }

    // ── TradingAgent boilerplate ─────────────────────────────────────────────────

    @Override public String getId()      { return agentId; }
    @Override public String getName()    { return "TechnicalTradingAgent[" + symbol + "]"; }
    @Override public boolean isRunning() { return status.get() == AgentStatus.ACTIVE; }

    @Override
    public void onEvent(Object event) {
        if (event instanceof KlineClosedEvent kce) {
            onKlineClosed(kce).subscribe(
                d  -> log.debug("[{}] onEvent decision: {}", agentId, d.action()),
                ex -> log.error("[{}] onEvent error: {}", agentId, ex.getMessage())
            );
        }
    }

    @Override
    @Deprecated
    public void executeTrade() {
        log.warn("[{}] executeTrade() called — agents are event-driven; ignoring.", agentId);
    }

    // ── private logic ─────────────────────────────────────────────────────────

    private AgentDecision evaluate(KlineClosedEvent event, RiskParams params) {
        addBar(event);
        int idx   = barSeries.getEndIndex();
        iterationCount.incrementAndGet();

        // ── 0. RiskGuard — fast pre-signal safety check ─────────────────────
        if (riskGuard != null && riskContextSupplier != null) {
            RiskContext riskCtx = riskContextSupplier.get();
            Optional<AgentDecision> override = riskGuard.evaluate(event, riskCtx);
            if (override.isPresent()) {
                log.info("[{}] RiskGuard override: {}", agentId, override.get().reasoning());
                return override.get();
            }
        }

        if (idx < warmupBars) {
            return AgentDecision.of(agentId, symbol, Action.HOLD, 50,
                    "Warming up (%d/%d bars)".formatted(idx, warmupBars),
                    params.quantity, params.stopLossPercent, params.takeProfitPercent);
        }

        // ── 1. Indicators ────────────────────────────────────────────────────
        double macdNow  = macdIndicator.getValue(idx).doubleValue();
        double macdPrev = macdIndicator.getValue(idx - 1).doubleValue();
        double sigNow   = signalLine.getValue(idx).doubleValue();
        double sigPrev  = signalLine.getValue(idx - 1).doubleValue();
        double rsi      = rsiIndicator.getValue(idx).doubleValue();
        double bbLowerVal = bbLower.getValue(idx).doubleValue();
        double bbUpperVal = bbUpper.getValue(idx).doubleValue();
        double closeVal = event.close().doubleValue();

        double histNow  = macdNow - sigNow;
        double histPrev = macdPrev - sigPrev;

        // ── 2. Entry signal ──────────────────────────────────────────────────
        boolean macdBullishCross = histNow > 0 && histPrev <= 0;
        boolean macdBearishCross = histNow < 0 && histPrev >= 0;

        Action action;
        String reasoning;

        if (macdBullishCross && rsi <= rsiOversold && closeVal <= bbLowerVal * 1.01) {
            action = Action.BUY;
            reasoning = "LONG entry: MACD bullish crossover, RSI=%.1f (oversold≤%.0f), close=%.4f ≤ bbLower=%.4f"
                    .formatted(rsi, rsiOversold, closeVal, bbLowerVal);
        } else if (macdBearishCross && rsi >= rsiOverbought && closeVal >= bbUpperVal * 0.99) {
            action = Action.SELL;
            reasoning = "SHORT entry: MACD bearish crossover, RSI=%.1f (overbought≥%.0f), close=%.4f ≥ bbUpper=%.4f"
                    .formatted(rsi, rsiOverbought, closeVal, bbUpperVal);
        } else {
            action = Action.HOLD;
            reasoning = "HOLD: MACD hist=%+.4f (prev=%+.4f), RSI=%.1f, close=%.4f [bbL=%.4f, bbU=%.4f]"
                    .formatted(histNow, histPrev, rsi, closeVal, bbLowerVal, bbUpperVal);
        }

        int confidence = action == Action.HOLD ? 50 : 80;
        log.debug("[{}] bar={} close={} {}", agentId, idx, closeVal, reasoning);
        return AgentDecision.of(agentId, symbol, action, confidence, reasoning,
                params.quantity, params.stopLossPercent, params.takeProfitPercent);
    }

    private static class RiskParams {
        final Double quantity;
        final Double stopLossPercent;
        final Double takeProfitPercent;

        RiskParams(Double quantity, Double stopLossPercent, Double takeProfitPercent) {
            this.quantity = quantity;
            this.stopLossPercent = stopLossPercent;
            this.takeProfitPercent = takeProfitPercent;
        }
    }

    private RiskParams getCurrentRiskParams() {
        if (riskContextSupplier != null) {
            RiskContext rc = riskContextSupplier.get();
            if (rc != null) {
                return new RiskParams(
                        rc.quantity() > 0 ? rc.quantity() : null,
                        rc.maxLossPercent() > 0 ? rc.maxLossPercent() : null,
                        rc.maxGainPercent() > 0 ? rc.maxGainPercent() : null);
            }
        }
        return new RiskParams(null, null, null);
    }

    private void addBar(KlineClosedEvent e) {
        ZonedDateTime endTime = ZonedDateTime.ofInstant(
                e.closeTime() != null ? e.closeTime() : Instant.now(), ZoneOffset.UTC);
        barSeries.addBar(
                Duration.ofMinutes(1), endTime,
                e.open().doubleValue(),
                e.high().doubleValue(),
                e.low().doubleValue(),
                e.close().doubleValue(),
                e.volume().doubleValue());
    }
}
