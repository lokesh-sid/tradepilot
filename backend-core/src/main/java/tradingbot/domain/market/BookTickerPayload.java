package tradingbot.domain.market;

import static java.util.Objects.*;

import java.math.BigDecimal;

/**
 * Typed payload for {@link StreamMarketDataEvent} events of type
 * {@link StreamMarketDataEvent.EventType#BOOK_TICKER}.
 *
 * <p>Both sides of the spread are preserved so that downstream consumers
 * (e.g. {@code OrderPlacementService}) can choose the correct fill price:
 * <ul>
 *   <li>{@link #ask()} — price a buyer pays (LONG entry / SHORT exit)</li>
 *   <li>{@link #bid()} — price a seller receives (SHORT entry / LONG exit)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * if (event.payload() instanceof BookTickerPayload btp) {
 *     double fillPrice = isBuy ? btp.ask().doubleValue() : btp.bid().doubleValue();
 * }
 * }</pre>
 */
public record BookTickerPayload(BigDecimal bid, BigDecimal ask) implements MarketDataPayload {

    /** Validates that bid and ask are non-null. Business rules are handled by MarketDataSanitizer. */
    public BookTickerPayload {
        requireNonNull(bid, "bid must not be null");
        requireNonNull(ask, "ask must not be null");
    }

    /** Convenience: mid-price for signal/indicator use (not for fill simulation). */
    public BigDecimal mid() {
        int scale = Math.max(bid.scale(), ask.scale()) + 1;
        return bid.add(ask).divide(java.math.BigDecimal.valueOf(2), scale,
                java.math.RoundingMode.HALF_UP);
    }
}
