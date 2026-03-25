package tradingbot.agent.domain.execution;

import tradingbot.agent.domain.model.AgentDecision;

/**
 * OrderExecutionGateway — unified order routing interface for agent decisions.
 *
 * <p>Bridges the gap between the agent's pure signal ({@link AgentDecision BUY/SELL/HOLD})
 * and the underlying exchange service ({@code FuturesExchangeService}).  Without this
 * abstraction, order routing logic is duplicated across live Kafka, backtest, and
 * paper-trading paths.
 *
 * <h3>Design rationale (P1 — unified execution)</h3>
 * <ul>
 *   <li><b>SRP</b>: agents produce decisions; this gateway executes them.
 *       Neither side knows about the other's internals.</li>
 *   <li><b>OCP</b>: new execution modes (e.g. DMA, FIX bridge) require only a
 *       new {@code OrderExecutionGateway} implementation — agents remain
 *       unchanged.</li>
 *   <li><b>DIP</b>: orchestrators and execution services depend on this
 *       interface, never on a concrete exchange client.</li>
 *   <li><b>LSP</b>: all implementations honour the same contract: HOLD → NOOP,
 *       BUY → open long / close short, SELL → open short / close long.</li>
 * </ul>
 *
 * <h3>Position model</h3>
 * The gateway tracks a <em>single directional position per symbol</em>:
 * <ul>
 *   <li>BUY when flat → enter LONG</li>
 *   <li>BUY when already LONG → NOOP (no pyramiding)</li>
 *   <li>BUY when SHORT → close SHORT (reverse not assumed)</li>
 *   <li>SELL when flat → enter SHORT</li>
 *   <li>SELL when already SHORT → NOOP</li>
 *   <li>SELL when LONG → close LONG</li>
 *   <li>HOLD → always NOOP</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * Implementations must be safe for concurrent calls from the {@code AgentOrchestrator}
 * scheduler.  The simplest approach is to confine mutable state behind synchronized
 * blocks or {@code AtomicReference}.
 */
public interface OrderExecutionGateway {

    /**
     * Executes the given agent decision against the configured exchange.
     *
     * @param decision     the agent's signal (BUY / SELL / HOLD)
     * @param symbol       trading pair (e.g. "BTCUSDT")
     * @param currentPrice current market price (close of the triggering candle)
     * @return result describing what happened — NOOP for HOLD or redundant signals
     */
    ExecutionResult execute(AgentDecision decision, String symbol, double currentPrice);

    /**
     * Returns {@code true} if there is an open position for the given symbol.
     */
    boolean hasOpenPosition(String symbol);

    /**
     * Returns the current position side ("LONG", "SHORT") or {@code null} if flat.
     */
    String getPositionSide(String symbol);

    /**
     * Returns the entry price of the current open position, or 0 if flat.
     */
    double getEntryPrice(String symbol);

    /**
     * Returns the quantity of the current open position, or 0 if flat.
     */
    double getPositionQuantity(String symbol);
}
