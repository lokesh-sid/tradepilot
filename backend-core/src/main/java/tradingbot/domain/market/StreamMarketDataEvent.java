package tradingbot.domain.market;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Reliable, immutable market data event for the internal reactive stream.
 * Renamed to StreamMarketDataEvent to avoid conflict with legacy bot events.
 */
public record StreamMarketDataEvent(
    String exchange,
    String symbol,
    EventType type,
    BigDecimal price,
    BigDecimal quantity,
    Instant timestamp,
    MarketDataPayload payload // Optional raw payload or specialized data (e.g. OrderBook)
) implements MarketEvent {

    public StreamMarketDataEvent {
        if (payload == null) {
            payload = new EmptyPayload();
        }
    }

    public enum EventType {
        TRADE,
        BOOK_TICKER,
        KLINE,
        ORDER_BOOK
    }
    
    public StreamMarketDataEvent withPayload(MarketDataPayload newPayload) {
        return new StreamMarketDataEvent(exchange, symbol, type, price, quantity, timestamp, newPayload);
    }

    @Override
    public BigDecimal volume() {
        return quantity;
    }
}
