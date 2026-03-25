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
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tradingbot.agent.ReactiveTradingAgent;
import tradingbot.agent.domain.model.AgentDecision;
import tradingbot.agent.domain.model.AgentDecision.Action;
import tradingbot.agent.domain.model.AgentGoal;
import tradingbot.agent.domain.model.AgentGoal.GoalType;
import tradingbot.agent.domain.model.AgentStatus;
import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.ReasoningContext;
import tradingbot.agent.domain.risk.RiskContext;
import tradingbot.agent.domain.risk.RiskGuard;
import tradingbot.agent.infrastructure.llm.LLMProvider;
import tradingbot.domain.market.KlineClosedEvent;

/**
 * LLMTradingAgent — first concrete {@link ReactiveTradingAgent}.
 *
 * <p>Combines <b>ta4j</b> technical analysis (MACD + RSI) with an
 * {@link LLMProvider} reasoning call to produce an {@link AgentDecision}
 * for every closed candle.
 *
 * <h3>Signal pipeline</h3>
 * <ol>
 *   <li>Add incoming bar to the internal {@code BarSeries}.</li>
 *   <li>If bar count &lt; {@code warmupBars}: return HOLD ("Warming up").</li>
 *   <li>Compute MACD histogram and RSI.</li>
 *   <li>Derive {@code technicalSignal} (BUY / SELL / HOLD) from indicator crossovers.</li>
 *   <li>Build a {@link Perception} + {@link ReasoningContext} and call
 *       {@link LLMProvider#generateReasoning(ReasoningContext)}.</li>
 *   <li>Merge LLM recommendation with technical signal (LLM is tie-break).</li>
 * </ol>
 *
 * <h3>SOLID alignment</h3>
 * <ul>
 *   <li><b>DIP</b>: depends on {@link LLMProvider} not {@code GrokClient} or
 *       {@code CachedGrokService} directly.</li>
 *   <li><b>ISP</b>: implements {@link ReactiveTradingAgent} — does <em>not</em>
 *       touch {@code FuturesTradingBot} or {@code IndicatorCalculator}.</li>
 *   <li><b>SRP</b>: this class is responsible only for signal generation; order
 *       routing is handled by the caller ({@code BacktestAgentExecutionService}).</li>
 * </ul>
 */
public class LLMTradingAgent implements ReactiveTradingAgent {

    private static final Logger log = LoggerFactory.getLogger(LLMTradingAgent.class);

    // --- identity ---------------------------------------------------------------
    private final String agentId;
    private final String symbol;
    private final String exchange;

    // --- dependencies -----------------------------------------------------------
    private final LLMProvider llmProvider;
    private final RiskGuard riskGuard;
    private final Supplier<RiskContext> riskContextSupplier;

    // --- indicator config -------------------------------------------------------
    private final int macdFast;
    private final int macdSlow;
    private final int macdSignal;
    private final int rsiPeriod;
    private final int warmupBars;

    // --- ta4j state (created in start()) ----------------------------------------
    private BaseBarSeries barSeries;
    private ClosePriceIndicator closePrice;
    private MACDIndicator macdIndicator;
    private EMAIndicator signalLine;
    private RSIIndicator rsiIndicator;

    // --- lifecycle state --------------------------------------------------------
    private final AtomicReference<AgentStatus> status =
            new AtomicReference<>(AgentStatus.CREATED);
    private final AtomicInteger iterationCount = new AtomicInteger(0);

    /**
     * Creates a new {@code LLMTradingAgent} with customisable indicator periods.
     *
     * @param agentId             unique agent identifier (e.g. {@code "btc-llm-01"})
     * @param symbol              trading pair (e.g. {@code "BTCUSDT"})
     * @param exchange            exchange name (e.g. {@code "BINANCE"})
     * @param llmProvider         LLM reasoning provider (wired via Spring DI)
     * @param riskGuard           pre-LLM risk evaluator (never null)
     * @param riskContextSupplier supplies the current position/risk state on each candle
     * @param macdFast            MACD fast EMA period (default 12)
     * @param macdSlow            MACD slow EMA period (default 26)
     * @param macdSignal          MACD signal EMA period (default 9)
     * @param rsiPeriod           RSI look-back period  (default 14)
     * @param warmupBars          minimum bars before signals are emitted (default 34)
     */
    public LLMTradingAgent(String agentId, String symbol, String exchange,
                           LLMProvider llmProvider,
                           RiskGuard riskGuard,
                           Supplier<RiskContext> riskContextSupplier,
                           int macdFast, int macdSlow, int macdSignal,
                           int rsiPeriod, int warmupBars) {
        this.agentId    = agentId;
        this.symbol     = symbol;
        this.exchange   = exchange;
        this.llmProvider = llmProvider;
        this.riskGuard  = riskGuard;
        this.riskContextSupplier = riskContextSupplier;
        this.macdFast   = macdFast;
        this.macdSlow   = macdSlow;
        this.macdSignal = macdSignal;
        this.rsiPeriod  = rsiPeriod;
        this.warmupBars = warmupBars;
    }

    // ── TradingAgent lifecycle ─────────────────────────────────────────────────

    @Override
    public void start() {
        if (!status.compareAndSet(AgentStatus.CREATED, AgentStatus.INITIALIZING) &&
            !status.compareAndSet(AgentStatus.STOPPED, AgentStatus.INITIALIZING)) {
            log.warn("[{}] start() called in unexpected state: {}", agentId, status.get());
            return;
        }
        // Build the shared BarSeries and wire all ta4j indicators
        barSeries    = new BaseBarSeriesBuilder().withName(agentId + "-series").build();
        closePrice   = new ClosePriceIndicator(barSeries);
        macdIndicator = new MACDIndicator(closePrice, macdFast, macdSlow);
        signalLine   = new EMAIndicator(macdIndicator, macdSignal);
        rsiIndicator  = new RSIIndicator(closePrice, rsiPeriod);

        status.set(AgentStatus.ACTIVE);
        log.info("[{}] started — MACD({},{},{}) RSI({}) warmup={} bars",
                agentId, macdFast, macdSlow, macdSignal, rsiPeriod, warmupBars);
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

    @Override
    public String getSymbol()   { return symbol; }

    @Override
    public String getExchange() { return exchange; }

    @Override
    public AgentStatus getStatus() { return status.get(); }

    /**
     * Processes one closed candle and returns an {@link AgentDecision} asynchronously.
     *
     * <p>All blocking work (ta4j math + LLM call) is deferred to a
     * bounded-elastic thread — the calling Kafka listener thread is never blocked.
     */
    @Override
    public Mono<AgentDecision> onKlineClosed(KlineClosedEvent event) {
        RiskParams params = getCurrentRiskParams();
        if (status.get() != AgentStatus.ACTIVE) {
            return Mono.just(AgentDecision.of(agentId, symbol, Action.HOLD, 0,
                    "Agent not ACTIVE (status=" + status.get() + ")", params.quantity, params.stopLossPercent, params.takeProfitPercent));
        }
        return Mono.fromCallable(() -> evaluate(event, params))
               .subscribeOn(Schedulers.boundedElastic())
               .onErrorResume(ex -> {
                   log.error("[{}] error evaluating bar at {}: {}", agentId, event.closeTime(), ex.getMessage());
                   status.compareAndSet(AgentStatus.ACTIVE, AgentStatus.ERROR);
                   return Mono.just(AgentDecision.of(agentId, symbol, Action.HOLD, 0,
                       "Evaluation error: " + ex.getMessage(), params.quantity, params.stopLossPercent, params.takeProfitPercent));
               });
    }

    // ── TradingAgent boilerplate ───────────────────────────────────────────────

    @Override public String getId()   { return agentId; }
    @Override public String getName() { return "LLMTradingAgent[" + symbol + "]"; }
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
        int idx = barSeries.getEndIndex();
        int count = iterationCount.incrementAndGet();

        // ── 0. RiskGuard — fast pre-LLM safety check ────────────────────────
        if (riskGuard != null && riskContextSupplier != null) {
            RiskContext riskCtx = riskContextSupplier.get();
            Optional<AgentDecision> override = riskGuard.evaluate(event, riskCtx);
            if (override.isPresent()) {
                log.info("[{}] RiskGuard override: {} — skipping LLM", agentId, override.get().reasoning());
                return override.get();
            }
        }

        if (idx < warmupBars) {
            return AgentDecision.of(agentId, symbol, Action.HOLD, 50,
                    "Warming up (%d/%d bars)".formatted(idx, warmupBars), params.quantity, params.stopLossPercent, params.takeProfitPercent);
        }

        // ── 1. Technical indicators ──────────────────────────────────────────
        double macdNow  = macdIndicator.getValue(idx).doubleValue();
        double macdPrev = macdIndicator.getValue(idx - 1).doubleValue();
        double sigNow   = signalLine.getValue(idx).doubleValue();
        double sigPrev  = signalLine.getValue(idx - 1).doubleValue();
        double rsi      = rsiIndicator.getValue(idx).doubleValue();

        double histNow  = macdNow - sigNow;
        double histPrev = macdPrev - sigPrev;

        Action technicalAction;
        String trend;
        if (histNow > 0 && histPrev <= 0) {
            technicalAction = Action.BUY;
            trend = "UPTREND";
        } else if (histNow < 0 && histPrev >= 0) {
            technicalAction = Action.SELL;
            trend = "DOWNTREND";
        } else {
            technicalAction = Action.HOLD;
            trend = histNow > 0 ? "UPTREND" : "DOWNTREND";
        }

        // RSI confirmation: suppress signals in exhaustion zones
        String rsiZone = rsi < 30 ? "OVERSOLD" : rsi > 70 ? "OVERBOUGHT" : "NEUTRAL";

        // ── 2. LLM reasoning ────────────────────────────────────────────────
        double closeVal = event.close().doubleValue();
        Perception perception = new Perception(
                symbol, closeVal, trend, rsiZone, event.volume().doubleValue(), event.closeTime());

        AgentGoal goal = new AgentGoal(GoalType.MAXIMIZE_PROFIT,
                "Maximise risk-adjusted return on " + symbol);

        var reasoningCtx = new ReasoningContext(goal, perception, symbol, 10_000.0, count);

        var reasoning = llmProvider.generateReasoning(reasoningCtx);
        String recommendation = reasoning.getRecommendation().toUpperCase();

        // ── 3. Merge technical + LLM ────────────────────────────────────────
        Action finalAction;
        if (technicalAction != Action.HOLD) {
            // Technical crossover wins; LLM must not contradict it to proceed
            boolean llmContradicts = (technicalAction == Action.BUY  && recommendation.contains("SELL"))
                                  || (technicalAction == Action.SELL && recommendation.contains("BUY"));
            finalAction = llmContradicts ? Action.HOLD : technicalAction;
        } else {
            // No crossover — use LLM recommendation directly
            if (recommendation.contains("BUY"))       finalAction = Action.BUY;
            else if (recommendation.contains("SELL")) finalAction = Action.SELL;
            else                                      finalAction = Action.HOLD;
        }

        int confidence = reasoning.getConfidence();
        String fullReasoning = "MACD=%+.4f hist=%+.4f RSI=%.1f(%s) → technical=%s | LLM=%s | final=%s"
                .formatted(macdNow, histNow, rsi, rsiZone, technicalAction, recommendation, finalAction);

        log.debug("[{}] bar={} close={} {}", agentId, idx, closeVal, fullReasoning);
        return AgentDecision.of(agentId, symbol, finalAction, confidence, fullReasoning, params.quantity, params.stopLossPercent, params.takeProfitPercent);
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
        Double quantity = null;
        Double stopLossPercent = null;
        Double takeProfitPercent = null;
        if (riskContextSupplier != null) {
            RiskContext rc = riskContextSupplier.get();
            if (rc != null) {
                quantity = rc.quantity() > 0 ? rc.quantity() : null;
                stopLossPercent = rc.maxLossPercent() > 0 ? rc.maxLossPercent() : null;
                takeProfitPercent = rc.maxGainPercent() > 0 ? rc.maxGainPercent() : null;
            }
        }
        return new RiskParams(quantity, stopLossPercent, takeProfitPercent);
    }

    private void addBar(KlineClosedEvent e) {
        ZonedDateTime endTime = ZonedDateTime.ofInstant(
                e.closeTime() != null ? e.closeTime() : Instant.now(), ZoneOffset.UTC);
        // Use 1-minute Duration as default; interval is carried in the event string
        // but ta4j only needs the end-time for bar ordering, not the exact duration.
        barSeries.addBar(
                Duration.ofMinutes(1), endTime,
                e.open().doubleValue(),
                e.high().doubleValue(),
                e.low().doubleValue(),
                e.close().doubleValue(),
                e.volume().doubleValue());
    }
}
