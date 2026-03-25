package tradingbot.domain.market;

/**
 * Sealed interface for all market data payloads.
 * Ensures compile-time exhaustiveness checks when processing different event payloads.
 */
public sealed interface MarketDataPayload permits BookTickerPayload, RawPayload, EmptyPayload {
}
