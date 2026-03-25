package tradingbot.bot.service;

/**
 * Ticker24hrStats - 24-hour market statistics for a symbol
 * 
 * Contains volume, price change, and other 24h statistics.
 */
public class Ticker24hrStats {
    
    private final String symbol;
    private final double volume;              // 24h traded volume
    private final double quoteVolume;         // 24h quote asset volume
    private final double priceChange;         // Absolute price change
    private final double priceChangePercent;  // Price change percentage
    private final double highPrice;           // 24h high price
    private final double lowPrice;            // 24h low price
    private final double openPrice;           // 24h open price
    private final double lastPrice;           // Current/last price
    
    private Ticker24hrStats(Builder builder) {
        this.symbol = builder.symbol;
        this.volume = builder.volume;
        this.quoteVolume = builder.quoteVolume;
        this.priceChange = builder.priceChange;
        this.priceChangePercent = builder.priceChangePercent;
        this.highPrice = builder.highPrice;
        this.lowPrice = builder.lowPrice;
        this.openPrice = builder.openPrice;
        this.lastPrice = builder.lastPrice;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getSymbol() { return symbol; }
    public double getVolume() { return volume; }
    public double getQuoteVolume() { return quoteVolume; }
    public double getPriceChange() { return priceChange; }
    public double getPriceChangePercent() { return priceChangePercent; }
    public double getHighPrice() { return highPrice; }
    public double getLowPrice() { return lowPrice; }
    public double getOpenPrice() { return openPrice; }
    public double getLastPrice() { return lastPrice; }
    
    public static class Builder {
        private String symbol;
        private double volume;
        private double quoteVolume;
        private double priceChange;
        private double priceChangePercent;
        private double highPrice;
        private double lowPrice;
        private double openPrice;
        private double lastPrice;
        
        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }
        
        public Builder volume(double volume) {
            this.volume = volume;
            return this;
        }
        
        public Builder quoteVolume(double quoteVolume) {
            this.quoteVolume = quoteVolume;
            return this;
        }
        
        public Builder priceChange(double priceChange) {
            this.priceChange = priceChange;
            return this;
        }
        
        public Builder priceChangePercent(double priceChangePercent) {
            this.priceChangePercent = priceChangePercent;
            return this;
        }
        
        public Builder highPrice(double highPrice) {
            this.highPrice = highPrice;
            return this;
        }
        
        public Builder lowPrice(double lowPrice) {
            this.lowPrice = lowPrice;
            return this;
        }
        
        public Builder openPrice(double openPrice) {
            this.openPrice = openPrice;
            return this;
        }
        
        public Builder lastPrice(double lastPrice) {
            this.lastPrice = lastPrice;
            return this;
        }
        
        public Ticker24hrStats build() {
            return new Ticker24hrStats(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("Ticker24hrStats{symbol='%s', volume=%.2f, priceChange=%.2f%%}", 
            symbol, volume, priceChangePercent);
    }
}
