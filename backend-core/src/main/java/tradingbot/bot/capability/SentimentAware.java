package tradingbot.bot.capability;

/**
 * Capability interface for bots that support sentiment-driven analysis.
 *
 * Only bots that implement this interface can be targeted by the sentiment endpoint.
 */
public interface SentimentAware {

    /** Returns whether sentiment analysis is currently active. */
    boolean isSentimentEnabled();

    /** Enables or disables sentiment analysis at runtime. */
    void enableSentimentAnalysis(boolean enable);
}
