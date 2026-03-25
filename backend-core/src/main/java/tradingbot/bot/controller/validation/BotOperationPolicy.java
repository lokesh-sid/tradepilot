package tradingbot.bot.controller.validation;

import org.springframework.stereotype.Component;

import tradingbot.agent.TradingAgent;
import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.controller.exception.BotAlreadyRunningException;
import tradingbot.bot.controller.exception.ConflictException;

/**
 * Encodes business-level operation policies for bot state transitions.
 *
 * Rules live here — NOT inside the domain object (FuturesTradingBot).
 * Keeping policies outside the domain ensures:
 *  - Domain objects remain pure (state + behaviour only)
 *  - All operability rules are auditable in one place
 *  - Rules can be changed without modifying the domain
 *
 * NOT responsible for:
 *  - HTTP concerns (delegated to controller)
 *  - Safety guardrails (delegated to TradingSafetyService)
 *  - Agent resolution (delegated to BotRequestValidator)
 */
@Component
public class BotOperationPolicy {

    /**
     * Asserts that a bot may be started.
     * Throws BotAlreadyRunningException (→ 409 Conflict) if the bot is already running.
     */
    public void assertCanStart(TradingAgent agent, String botId) {
        if (agent != null && agent.isRunning()) {
            throw new BotAlreadyRunningException();
        }
    }

    /**
     * Asserts that configuration may be updated.
     * Configuration changes are only safe when the bot is fully stopped.
     */
    public void assertCanReconfigure(TradingAgent agent, String botId) {
        if (agent != null && agent.isRunning()) {
            throw new IllegalStateException(
                "Bot " + botId + " is currently running. Stop the bot before changing its configuration."
            );
        }
    }

    /**
     * Asserts that leverage may be updated.
     * Changing leverage against an open live position risks immediate exchange rejection
     * or unintended liquidation.
     */
    public void assertCanUpdateLeverage(FuturesTradingBot bot, String botId) {
        if (bot.isRunning() && hasActivePosition(bot)) {
            throw new IllegalStateException(
                "Bot " + botId + " has an open position. Close the position before changing leverage."
            );
        }
    }

    /**
     * Asserts that a running bot may be stopped.
     * Throws ConflictException (→ 409) if the bot is already stopped.
     */
    public void assertCanStop(FuturesTradingBot bot, String botId) {
        if (!bot.isRunning()) {
            throw new ConflictException(
                "Bot " + botId + " is already stopped."
            );
        }
    }

    /**
     * Asserts that a running bot may be paused.
     * Throws ConflictException (→ 409) if the bot is not running.
     */
    public void assertCanPause(FuturesTradingBot bot, String botId) {
        if (!bot.isRunning()) {
            throw new ConflictException(
                "Bot " + botId + " is not running. Cannot pause a stopped bot."
            );
        }
    }

    // -----------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------

    private boolean hasActivePosition(FuturesTradingBot bot) {
        return bot.getPositionStatus() != null
            && !bot.getPositionStatus().equalsIgnoreCase("FLAT");
    }
}
