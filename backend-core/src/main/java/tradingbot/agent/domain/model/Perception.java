package tradingbot.agent.domain.model;

import java.time.Instant;

/**
 * Perception - What the agent observes about the market
 */
public class Perception {
    
    private final String symbol;
    private final double currentPrice;
    private final String trend;
    private final String sentiment;
    private final double volume;
    private final Instant timestamp;
    /** Best ask (price a buyer pays). Defaults to {@code currentPrice} when not from a book-ticker event. */
    private final double askPrice;
    /** Best bid (price a seller receives). Defaults to {@code currentPrice} when not from a book-ticker event. */
    private final double bidPrice;
    
    /**
     * Primary constructor used when bid/ask spread is known (e.g. from a BookTickerPayload).
     */
    public Perception(String symbol, double currentPrice, String trend,
                     String sentiment, double volume, Instant timestamp,
                     double bidPrice, double askPrice) {
        this.symbol = symbol;
        this.currentPrice = currentPrice;
        this.trend = trend;
        this.sentiment = sentiment;
        this.volume = volume;
        this.timestamp = timestamp;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
    }

    /**
     * Convenience constructor for callers that only have a single price (e.g. KLine close).
     * Both {@code bidPrice} and {@code askPrice} default to {@code currentPrice}.
     */
    public Perception(String symbol, double currentPrice, String trend, 
                     String sentiment, double volume, Instant timestamp) {
        this(symbol, currentPrice, trend, sentiment, volume, timestamp, currentPrice, currentPrice);
    }
    
    public String getSymbol() { return symbol; }
    public double getCurrentPrice() { return currentPrice; }
    public String getTrend() { return trend; }
    public String getSentiment() { return sentiment; }
    public double getVolume() { return volume; }
    public Instant getTimestamp() { return timestamp; }
    /** Price a buyer pays — use for LONG entry / SHORT exit fill simulation. */
    public double getAskPrice() { return askPrice; }
    /** Price a seller receives — use for SHORT entry / LONG exit fill simulation. */
    public double getBidPrice() { return bidPrice; }
}
