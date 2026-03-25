package tradingbot.agent.domain.risk;

import java.util.Optional;

import tradingbot.agent.domain.model.AgentDecision;
import tradingbot.domain.market.KlineClosedEvent;

/**
 * RiskGuard — pre-LLM safety check that evaluates hard risk limits
 * (stop-loss, take-profit) before any slow strategic reasoning occurs.
 *
 * <p>The contract is: given the current market event and the agent's
 * active position state, determine if an <strong>immediate</strong>
 * exit is required to protect capital.
 *
 * <h3>Design rationale (P0 — capital protection)</h3>
 * <ul>
 *   <li>LLM inference takes 1-5 seconds.  A flash crash can wipe a
 *       leveraged position in milliseconds.</li>
 *   <li>By placing the risk check <em>before</em> the LLM call in the
 *       agent pipeline, we guarantee sub-millisecond reaction to
 *       hard limits regardless of LLM latency.</li>
 *   <li>If an override is returned, the agent short-circuits and
 *       <strong>never</strong> invokes the LLM.  This also saves LLM
 *       quota on exits that are mechanically deterministic.</li>
 * </ul>
 *
 * <h3>SOLID alignment</h3>
 * <ul>
 *   <li><b>SRP</b>: this interface is solely responsible for pre-trade
 *       risk evaluation.</li>
 *   <li><b>OCP</b>: new risk rules (e.g. max-drawdown, time-based
 *       exit) can be added by implementing this interface — existing
 *       agents remain unchanged.</li>
 *   <li><b>DIP</b>: agents depend on this abstraction, not on any
 *       concrete risk implementation.</li>
 * </ul>
 */
public interface RiskGuard {

    /**
     * Evaluates whether an immediate exit is required based on hard
     * risk limits.
     *
     * @param event    the closed candle just received
     * @param context  current position / risk state for this agent
     * @return {@link Optional#empty()} if no override (proceed to LLM),
     *         or a present {@link AgentDecision} that should be used
     *         <em>instead of</em> the LLM decision (immediate exit)
     */
    Optional<AgentDecision> evaluate(KlineClosedEvent event, RiskContext context);
}
