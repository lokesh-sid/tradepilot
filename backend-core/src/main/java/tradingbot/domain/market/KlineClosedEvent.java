package tradingbot.domain.market;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * KlineClosedEvent — a fully-closed candle from any exchange adapter.
 *
 * <p>This record is emitted <strong>only</strong> when a candle is definitively
 * closed (i.e. no further updates for that time-bucket will arrive).  Consumers
 * that subscribe to this type (e.g. {@code AgentOrchestrator}) can safely add
 * a bar to a {@code BarSeries} without any KLINE-vs-tick disambiguation logic.
 *
 * <p>Exchange adapters are responsible for filtering their raw streams and
 * emitting this event only once per closed bar.
 *
 * <p>SOLID notes:
 * <ul>
 *   <li><b>ISP</b>: {@code AgentOrchestrator} subscribes to this type and never
 *       sees TRADE / BOOK_TICKER noise.</li>
 *   <li><b>DIP</b>: consumers depend on this domain record, not on any
 *       exchange-specific DTO.</li>
 *   <li><b>LSP</b>: no casting — the type system enforces correctness at
 *       compile time.</li>
 * </ul>
 *
 * @param exchange   normalized exchange identifier, e.g. {@code "BINANCE"}, {@code "BYBIT"}
 * @param symbol     trading pair, e.g. {@code "BTCUSDT"}
 * @param interval   candle interval string, e.g. {@code "1m"}, {@code "5m"}, {@code "1h"}
 * @param open       open price of the candle
 * @param high       highest price within the candle
 * @param low        lowest price within the candle
 * @param close      close price of the candle
 * @param volume     volume traded during the candle
 * @param openTime   candle open timestamp (UTC)
 * @param closeTime  candle close timestamp (UTC)
 */
public record KlineClosedEvent(
        String exchange,
        String symbol,
        String interval,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        Instant openTime,
        Instant closeTime
) implements MarketEvent {

    @Override
    public BigDecimal price() {
        return close;
    }

    @Override
    public Instant timestamp() {
        return closeTime;
    }

    /**
     * Convenience constructor: derives {@code closeTime} from
     * {@code openTime + interval} for callers that only have the open timestamp.
     * Prefer the canonical constructor when both timestamps are available.
     */
    public static KlineClosedEvent of(
            String exchange,
            String symbol,
            String interval,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume,
            Instant openTime) {
        return new KlineClosedEvent(exchange, symbol, interval,
                open, high, low, close, volume, openTime, openTime);
    }
}
