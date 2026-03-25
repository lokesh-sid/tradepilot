package tradingbot.domain.market;

/**
 * Represents the absence of a specialized payload or when a payload is disabled (e.g. debug off).
 */
public record EmptyPayload() implements MarketDataPayload {
}
