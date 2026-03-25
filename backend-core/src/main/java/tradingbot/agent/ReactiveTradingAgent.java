package tradingbot.agent;

import reactor.core.publisher.Mono;
import tradingbot.agent.domain.model.AgentDecision;
import tradingbot.agent.domain.model.AgentStatus;
import tradingbot.domain.market.KlineClosedEvent;

/**
 * ReactiveTradingAgent — the reactive, event-driven trading agent contract.
 *
 * <p>This is a <em>sub-interface</em> of {@link TradingAgent}.  By keeping the
 * contract separate we achieve the following SOLID properties:
 *
 * <ul>
 *   <li><b>ISP</b>: callers that only need lifecycle control ({@code start},
 *       {@code stop}) continue to depend on the narrower {@link TradingAgent}.
 *       Only {@code AgentOrchestrator} and components that drive the reactive
 *       loop need to depend on this richer type.</li>
 *   <li><b>OCP</b>: {@link tradingbot.bot.FuturesTradingBot} is untouched;
 *       new agent implementations simply implement this interface.</li>
 *   <li><b>LSP</b>: anything implementing {@code ReactiveTradingAgent} is
 *       guaranteed to also be a valid {@link TradingAgent} — substitutability
 *       is enforced by the type hierarchy.</li>
 *   <li><b>DIP</b>: {@code AgentOrchestrator} depends on this abstraction, not
 *       on any concrete agent class.</li>
 * </ul>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>{@link #onKlineClosed(KlineClosedEvent)} must be non-blocking.  All
 *       heavy work (LLM calls, DB writes) must happen inside the returned
 *       {@code Mono} and be subscribed on a bounded-elastic scheduler.</li>
 *   <li>{@link #pause()} and {@link #resume()} are synchronous; they only
 *       flip the internal status flag.  No orders are cancelled on pause.</li>
 *   <li>{@link #getStatus()} must be thread-safe (idempotent read).</li>
 * </ul>
 */
public interface ReactiveTradingAgent extends TradingAgent {

    /**
     * Returns the trading pair symbol this agent is configured for,
     * e.g. {@code "BTCUSDT"}.
     */
    String getSymbol();

    /**
     * Returns the exchange identifier this agent targets,
     * e.g. {@code "BINANCE"}, {@code "BYBIT"}.
     */
    String getExchange();

    /**
     * Returns the current lifecycle status of this agent.
     * Must be thread-safe.
     */
    AgentStatus getStatus();

    /**
     * Reacts to a fully-closed candle.
     *
     * <p>The implementation should:
     * <ol>
     *   <li>Feed the OHLCV bar into the ta4j {@code BarSeries}.</li>
     *   <li>Run technical indicator logic.</li>
     *   <li>Optionally invoke the {@code LLMProvider} for reasoning.</li>
     *   <li>Return the decision wrapped in a cold {@code Mono}.</li>
     * </ol>
     *
     * <p>The caller ({@code AgentOrchestrator}) is responsible for subscribing
     * on the appropriate scheduler.
     *
     * @param event the closed candle event — guaranteed non-null and fully populated
     * @return a cold {@code Mono} that emits exactly one {@link AgentDecision}
     *         and then completes, or errors if a fatal condition is encountered
     */
    Mono<AgentDecision> onKlineClosed(KlineClosedEvent event);

    /**
     * Temporarily suspends the agent.  While {@link AgentStatus#PAUSED}, the
     * orchestrator skips this agent when dispatching {@link KlineClosedEvent}s.
     * A paused agent does not issue any orders.
     */
    void pause();

    /**
     * Resumes a {@link AgentStatus#PAUSED} agent.  No-op if already
     * {@link AgentStatus#ACTIVE}.
     */
    void resume();
}
