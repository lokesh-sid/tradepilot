package tradingbot.agent.impl;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import tradingbot.agent.ReactiveTradingAgent;
import tradingbot.agent.TradingAgentFactory;
import tradingbot.agent.domain.risk.RiskContext;
import tradingbot.agent.domain.risk.RiskGuard;
import tradingbot.agent.infrastructure.llm.LLMProvider;
import tradingbot.config.TradingConfig;
/**
 * LLMTradingAgentFactory — default {@link TradingAgentFactory} implementation.
 *
 * <p>Creates {@link LLMTradingAgent} instances wired with the active
 * {@link LLMProvider} bean.  By virtue of Spring's {@code @Primary} annotation
 * on {@code CachedGrokService}, backtests automatically use the cached/synthetic
 * LLM path — no code changes needed when switching profiles.
 *
 * <h3>Profile behaviour</h3>
 * <ul>
 *   <li><b>backtest</b>: {@code LLMProvider} → {@code CachedGrokService}
 *       (Redis L1 → File L2 → synthetic fallback; real API never called).</li>
 *   <li><b>prod / dev</b>: {@code LLMProvider} → {@code GrokClient}
 *       (live Grok xAI API).</li>
 * </ul>
 *
 * <h3>SOLID alignment</h3>
 * <ul>
 *   <li><b>DIP</b>: this class is the only place that {@code new LLMTradingAgent(...)}
 *       appears — all callers depend on {@link TradingAgentFactory}.</li>
 *   <li><b>OCP</b>: to add a BollingerBand agent factory, create a new
 *       {@code @Component} implementation of {@link TradingAgentFactory} and
 *       qualify it via {@code @Qualifier}; nothing here changes.</li>
 * </ul>
 */
@Primary
@Component
public class LLMTradingAgentFactory implements TradingAgentFactory {

    private static final Logger log = LoggerFactory.getLogger(LLMTradingAgentFactory.class);

    // Standard MACD(12,26,9) + RSI(14) defaults — configurable per agent via TradingConfig later
    private static final int MACD_FAST   = 12;
    private static final int MACD_SLOW   = 26;
    private static final int MACD_SIGNAL = 9;
    private static final int RSI_PERIOD  = 14;
    private static final int WARMUP_BARS = 34; // slowPeriod + signalPeriod

    private final LLMProvider llmProvider;
    private final RiskGuard riskGuard;

    public LLMTradingAgentFactory(LLMProvider llmProvider, RiskGuard riskGuard) {
        this.llmProvider = llmProvider;
        this.riskGuard = riskGuard;
    }

    /**
     * Creates and starts a new {@link LLMTradingAgent} for the symbol/exchange
     * specified in {@code config}.
     *
     * <p>The returned agent is in {@code ACTIVE} state — {@code start()} is
     * called inside this method so callers can dispatch events immediately.
     */
    @Override
    public ReactiveTradingAgent create(TradingConfig config) {
        String agentId = "llm-" + config.getSymbol().toLowerCase() + "-01";
        String exchange = "BINANCE"; // default; Phase 3 will derive from TradingConfig

        log.info("[LLMTradingAgentFactory] creating agent id={} symbol={} llm={}",
                agentId, config.getSymbol(), llmProvider.getProviderName());

        // RiskContext supplier starts with no position; the OrderExecutionGateway
        // (P1) will update this dynamically as positions open/close.
        // For now, a safe default: no open position = no risk overrides.
        var riskCtxRef = new AtomicReference<>(
            RiskContext.noPosition(agentId, config.getSymbol()));

        LLMTradingAgent agent = new LLMTradingAgent(
                agentId, config.getSymbol(), exchange, llmProvider,
                riskGuard, riskCtxRef::get,
                MACD_FAST, MACD_SLOW, MACD_SIGNAL, RSI_PERIOD, WARMUP_BARS);

        agent.start(); // CREATED → ACTIVE
        return agent;
    }

    @Override
    public String describe() {
        return "LLMTradingAgentFactory[provider=" + llmProvider.getProviderName()
                + ", MACD(" + MACD_FAST + "," + MACD_SLOW + "," + MACD_SIGNAL
                + "), RSI(" + RSI_PERIOD + "), warmup=" + WARMUP_BARS + "]";
    }
}
