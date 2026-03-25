package tradingbot.domain.market;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * MarketEvent — unified interface for all market data events in the system.
 * 
 * <p>Allows polymorphic handling of both real-time stream events ({@link StreamMarketDataEvent})
 * and aggregated candle events ({@link KlineClosedEvent}).
 */
public interface MarketEvent {
    
    String exchange();
    
    String symbol();
    
    /**
     * The "primary" price associated with this event.
     * For a trade, it's the trade price.
     * For a kline, it's the close price.
     */
    BigDecimal price();
    
    /**
     * Volume or quantity associated with the event.
     */
    BigDecimal volume();
    
    Instant timestamp();
}
