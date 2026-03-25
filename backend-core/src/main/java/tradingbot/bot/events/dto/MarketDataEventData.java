package tradingbot.bot.events.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event data for market data events.
 */
public class MarketDataEventData {
    
    @JsonProperty("symbol")
    private String symbol;
    
    @JsonProperty("price")
    private Double price;
    
    @JsonProperty("volume")
    private Double volume;
    
    @JsonProperty("timestamp")
    private Long timestamp;
    
    @JsonProperty("bidPrice")
    private Double bidPrice;
    
    @JsonProperty("askPrice")
    private Double askPrice;
    
    @JsonProperty("high24h")
    private Double high24h;
    
    @JsonProperty("low24h")
    private Double low24h;
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public Double getPrice() {
        return price;
    }
    
    public void setPrice(Double price) {
        this.price = price;
    }
    
    public Double getVolume() {
        return volume;
    }
    
    public void setVolume(Double volume) {
        this.volume = volume;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    public Double getBidPrice() {
        return bidPrice;
    }
    
    public void setBidPrice(Double bidPrice) {
        this.bidPrice = bidPrice;
    }
    
    public Double getAskPrice() {
        return askPrice;
    }
    
    public void setAskPrice(Double askPrice) {
        this.askPrice = askPrice;
    }
    
    public Double getHigh24h() {
        return high24h;
    }
    
    public void setHigh24h(Double high24h) {
        this.high24h = high24h;
    }
    
    public Double getLow24h() {
        return low24h;
    }
    
    public void setLow24h(Double low24h) {
        this.low24h = low24h;
    }
    
    @Override
    public String toString() {
        return "MarketDataEventData{" +
                "symbol='" + symbol + '\'' +
                ", price=" + price +
                ", volume=" + volume +
                ", timestamp=" + timestamp +
                ", bidPrice=" + bidPrice +
                ", askPrice=" + askPrice +
                ", high24h=" + high24h +
                ", low24h=" + low24h +
                '}';
    }
}
