package tradingbot.bot.capability;

/**
 * Capability interface for bots that support dynamic leverage adjustment.
 *
 * Only bots that implement this interface can be passed to the leverage endpoint.
 * The controller resolves via BotRequestValidator.resolveAgentAs(botId, LeverageConfigurable.class),
 * so the HTTP layer never needs to know the concrete bot type.
 */
public interface LeverageConfigurable {

    /** Returns the bot's current leverage multiplier. */
    int getCurrentLeverage();

    /** Applies a new leverage value at runtime. */
    void setDynamicLeverage(int leverage);
}
