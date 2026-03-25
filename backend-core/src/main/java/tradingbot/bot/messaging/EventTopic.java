package tradingbot.bot.messaging;

/**
 * Enum representing different event topics for the trading bot messaging system.
 * 
 * This provides better type safety and centralized management of topic names
 * compared to string constants. In production with Kafka, these map to
 * actual Kafka topic names (e.g., trading.signals, trading.executions, etc.)
 */
public enum EventTopic {
    
    TRADE_SIGNALS("trading.signals"),
    TRADE_EXECUTION("trading.executions"),
    RISK_EVENTS("trading.risk"),
    MARKET_DATA("trading.market-data"),
    BOT_STATUS("trading.bot-status");
    
    private final String topicName;
    
    EventTopic(String topicName) {
        this.topicName = topicName;
    }
    
    /**
     * Gets the actual topic name string.
     * 
     * @return the topic name used for publishing/subscribing
     */
    public String getTopicName() {
        return topicName;
    }
    
    /**
     * Finds an EventTopic by its topic name.
     * 
     * @param topicName the topic name to search for
     * @return the matching EventTopic, or null if not found
     */
    public static EventTopic fromTopicName(String topicName) {
        for (EventTopic topic : values()) {
            if (topic.topicName.equals(topicName)) {
                return topic;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return topicName;
    }
}