package tradingbot.domain.market;

import static java.util.Objects.*;

/**
 * Encapsulates raw payload data (e.g. JSON String or JsonNode) from exchange connections
 * that have not been strongly typed.
 */
public record RawPayload(Object data) implements MarketDataPayload {
    public RawPayload {
        requireNonNull(data, "raw data must not be null");
    }
}
