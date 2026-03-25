package tradingbot.bot.events;

import java.util.Map;

/**
 * Event representing market data updates.
 * Published when new market data is received from exchanges.
 */
public class MarketDataEvent extends TradingEvent {
    
    private String symbol;
    private double price;
    private double volume;
    private String timeframe;
    private Map<String, Object> ohlcv; // Open, High, Low, Close, Volume data
    private String source; // binance, bybit, etc.
    
    public MarketDataEvent() {
        super();
    }
    
    public MarketDataEvent(String botId, String symbol, double price) {
        super(botId, "MARKET_DATA");
        this.symbol = symbol;
        this.price = price;
    }
    
    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public double getPrice() {
        return price;
    }
    
    public void setPrice(double price) {
        this.price = price;
    }
    
    public double getVolume() {
        return volume;
    }
    
    public void setVolume(double volume) {
        this.volume = volume;
    }
    
    public String getTimeframe() {
        return timeframe;
    }
    
    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }
    
    public Map<String, Object> getOhlcv() {
        return ohlcv;
    }
    
    public void setOhlcv(Map<String, Object> ohlcv) {
        this.ohlcv = ohlcv;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    @Override
    public String toString() {
        return "MarketDataEvent{" +
                "symbol='" + symbol + '\'' +
                ", price=" + price +
                ", volume=" + volume +
                ", timeframe='" + timeframe + '\'' +
                ", source='" + source + '\'' +
                ", eventId='" + getEventId() + '\'' +
                '}';
    }
}