package tradingbot.agent;

public interface TradingAgent {
    /**
     * Unique identifier for this agent instance.
     */
    String getId();

    /**
     * Human-readable name for this agent.
     */
    String getName();

    /**
     * Starts the agent's trading activities.
     */
    void start();

    /**
     * Stops the agent's trading activities.
     */
    void stop();

    /**
     * Checks if the agent is currently active.
     */
    boolean isRunning();

    /**
     * Processes incoming events (MarketData, News, etc.) to generate trading signals.
     * @param event The event to process
     */
    void onEvent(Object event);

    /**
     * Manually triggers a trade execution logic.
     * @deprecated Agents should react to market data, not manual triggers.
     */
    @Deprecated
    void executeTrade();
}
