package tradingbot.bot.controller.validation;

import org.springframework.stereotype.Component;

import tradingbot.agent.TradingAgent;
import tradingbot.agent.infrastructure.repository.AgentEntity;
import tradingbot.agent.infrastructure.repository.JpaAgentRepository;
import tradingbot.agent.manager.AgentManager;
import tradingbot.bot.controller.exception.BotNotFoundException;

/**
 * Centralises cross-cutting concerns for bot controller endpoints:
 *
 *  1. Agent resolution — loads from memory; falls back to DB refresh if not yet loaded.
 *  2. Type/capability safety — generic resolveAgentAs guards ClassCastException.
 *  3. Paper-mode resolution — derives effective paper flag from request + persisted entity.
 *
 * NOT responsible for:
 *  - HTTP concerns (status codes, response bodies)  → controller
 *  - Operation-level business rules (open position) → BotOperationPolicy
 *  - Safety guardrails (mainnet opt-in)             → TradingSafetyService
 *  - Persistence write-back                         → AgentManager / AgentRepository
 */
@Component
public class BotRequestValidator {

    private final AgentManager agentManager;
    private final JpaAgentRepository agentRepository;

    public BotRequestValidator(AgentManager agentManager, JpaAgentRepository agentRepository) {
        this.agentManager = agentManager;
        this.agentRepository = agentRepository;
    }

    // ------------------------------------------------------------------
    // 1. Agent resolution
    // ------------------------------------------------------------------

    /**
     * Resolves a loaded agent, attempting a DB refresh if not yet in memory.
     * Throws BotNotFoundException (→ 404) if neither the memory cache nor the DB
     * has a record for this botId.
     */
    public TradingAgent resolveAgent(String botId) {
        TradingAgent agent = agentManager.getAgent(botId);
        if (agent == null) {
            if (!agentRepository.existsById(botId)) {
                throw new BotNotFoundException(botId);
            }
            agentManager.refreshAgent(botId);
            agent = agentManager.getAgent(botId);
            if (agent == null) {
                throw new BotNotFoundException(botId);
            }
        }
        return agent;
    }

    // ------------------------------------------------------------------
    // 2. Capability-safe resolution (generics — works for both concrete
    //    classes AND capability interfaces)
    // ------------------------------------------------------------------

    /**
     * Resolves an agent and verifies it satisfies the requested capability.
     *
     * <p>The {@code capability} parameter should be a <strong>capability interface</strong>
     * (e.g. {@code LeverageConfigurable.class}), not a concrete class. This keeps the
     * controller agnostic to the actual bot type — it only needs to know what the bot
     * <em>can do</em>, not what it <em>is</em>.
     *
     * <p>Example usage in a controller:
     * <pre>{@code
     *     LeverageConfigurable bot = validator.resolveAgentAs(botId, LeverageConfigurable.class);
     *     bot.setDynamicLeverage(request.getLeverage());
     * }</pre>
     *
     * @param botId      the bot identifier to resolve
     * @param capability the interface or class the agent must implement
     * @param <T>        the capability type
     * @return the agent cast to {@code T}
     * @throws BotNotFoundException      if the bot does not exist
     * @throws UnsupportedOperationException if the bot does not support the capability (→ 400)
     */
    public <T> T resolveAgentAs(String botId, Class<T> capability) {
        TradingAgent agent = resolveAgent(botId);
        if (!capability.isInstance(agent)) {
            throw new UnsupportedOperationException(
                String.format(
                    "Bot %s (type: %s) does not support the '%s' operation",
                    botId,
                    agent.getClass().getSimpleName(),
                    capability.getSimpleName()
                )
            );
        }
        return capability.cast(agent);
    }

    // ------------------------------------------------------------------
    // 3. Paper-mode resolution
    // ------------------------------------------------------------------

    /**
     * Resolves the effective paper-mode for a start or state-update request.
     *
     * <p>Resolution priority:
     * <ol>
     *   <li>If {@code requestPaperMode} is explicitly provided, use it.</li>
     *   <li>If {@code requestPaperMode} is null, inherit from the persisted entity type.</li>
     *   <li>If the entity is missing (should not happen), default to {@code true} (safe).</li>
     * </ol>
     *
     * <p>Note: this method does NOT call TradingSafetyService. The caller is responsible
     * for passing the resolved value to the safety layer.
     *
     * @param botId           the bot identifier
     * @param requestPaperMode the value from the HTTP request body (may be null if omitted)
     * @return the resolved paper-mode flag
     */
    public boolean resolvePaperMode(String botId, Boolean requestPaperMode) {
        boolean isExistingPaper = agentRepository.findById(botId)
            .map(entity -> entity.getExecutionMode() == AgentEntity.ExecutionMode.FUTURES_PAPER)
            .orElse(true); // safe default: treat missing entity as paper

        return requestPaperMode != null ? requestPaperMode : isExistingPaper;
    }
}
