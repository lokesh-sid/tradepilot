package tradingbot.agent;

import tradingbot.config.TradingConfig;

/**
 * TradingAgentFactory — DIP port for creating {@link ReactiveTradingAgent} instances.
 *
 * <p>Introducing this interface breaks the direct instantiation of
 * {@code FuturesTradingBot} inside {@code BacktestService} (the most prominent
 * <b>DIP violation</b> identified in the Phase 1 code review).
 *
 * <h3>SOLID alignment</h3>
 * <ul>
 *   <li><b>DIP</b>: {@code BacktestService} / {@code AgentOrchestrator} declare a
 *       dependency on this interface, not on any concrete agent class.</li>
 *   <li><b>OCP</b>: Switching from {@code FuturesTradingBot} to
 *       {@code LLMTradingAgent} (or any future implementation) requires only a
 *       different {@code @Bean} binding — no caller changes.</li>
 *   <li><b>SRP</b>: Construction logic is encapsulated in the factory; callers
 *       focus on <em>using</em> an agent rather than <em>assembling</em> one.</li>
 * </ul>
 *
 * <h3>Spring wiring</h3>
 * <pre>{@code
 * // Backtest profile  →  LLMTradingAgentFactory  (CachedGrokService, ta4j MACD/RSI)
 * // Production profile →  LiveTradingAgentFactory (GrokClient, live exchange wiring)
 * }</pre>
 */
public interface TradingAgentFactory {

    /**
     * Creates a new, fully initialised {@link ReactiveTradingAgent} for the
     * symbol / exchange combination described by {@code config}.
     *
     * <p>The returned agent is in the {@code CREATED} state.  Callers are
     * responsible for calling {@link TradingAgent#start()} when ready.
     *
     * @param config non-null trading configuration carrying symbol, exchange,
     *               interval, initial capital, and strategy parameters
     * @return a fresh {@link ReactiveTradingAgent} ready to process
     *         {@link tradingbot.domain.market.KlineClosedEvent}s
     */
    ReactiveTradingAgent create(TradingConfig config);

    /**
     * Human-readable description of this factory — used in logs.
     * Example: {@code "LLMTradingAgentFactory[backtest, CachedGrok]"}.
     */
    String describe();
}
