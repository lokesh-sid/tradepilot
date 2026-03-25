package tradingbot.infrastructure.marketdata;

import static java.math.RoundingMode.*;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tradingbot.domain.market.BookTickerPayload;
import tradingbot.domain.market.EmptyPayload;
import tradingbot.domain.market.RawPayload;
import tradingbot.domain.market.StreamMarketDataEvent;

@Component
public class MarketDataSanitizer {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSanitizer.class);

    @Value("${market.data.max-spread-percent:5.0}")
    private double maxSpreadPercent;

    /** Returns true if the event is safe for downstream use*/
    public boolean isValid(StreamMarketDataEvent event) {
        if (event.exchange() == null || event.exchange().isBlank()) return false;
        if (event.symbol() == null || event.symbol().isBlank()) return false;
        if (event.type() == null) return false;
        if (event.price() == null || event.price().signum() <= 0) return false;
        if (event.timestamp() == null) return false;

        // TRADE events must carry a positive quantity
        if (event.type() == StreamMarketDataEvent.EventType.TRADE
                && (event.quantity() == null || event.quantity().signum() <= 0)) {
            return false;
        }

        // Payload-specific checks via pattern matching
        return switch (event.payload()) {
            case BookTickerPayload btp -> isValidSpread(btp);
            case RawPayload rp      -> true;
            case EmptyPayload ep    -> true;
        };
    }

    /** Auto-corrects crossed spread; logs a warning instead of throwing. */
    public StreamMarketDataEvent sanitize(StreamMarketDataEvent event) {
        return switch (event.payload()) {
            case BookTickerPayload btp -> sanitizeBookTicker(event, btp);
            case RawPayload rp   -> event;
            case EmptyPayload ep -> event;
        };
    }

    private StreamMarketDataEvent sanitizeBookTicker(StreamMarketDataEvent event, BookTickerPayload btp) {
        if (btp.bid().compareTo(btp.ask()) <= 0) return event;

        log.warn("[{}] Crossed book received bid={} ask={} — correcting",
                event.exchange(), btp.bid(), btp.ask());

        BookTickerPayload corrected = new BookTickerPayload(btp.ask(), btp.bid());
        return event.withPayload(corrected);
    }

    private boolean isValidSpread(BookTickerPayload btp) {
        if (btp.bid().signum() <= 0 || btp.ask().signum() <= 0) return false;
        // Reject absurdly wide spreads (e.g. stale data from exchange reconnect)
        BigDecimal spreadPct = btp.ask().subtract(btp.bid())
                .divide(btp.ask(), 6, HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return spreadPct.doubleValue() <= maxSpreadPercent;
    }
}
