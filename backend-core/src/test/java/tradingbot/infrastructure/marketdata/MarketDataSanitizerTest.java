package tradingbot.infrastructure.marketdata;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import tradingbot.domain.market.BookTickerPayload;
import tradingbot.domain.market.EmptyPayload;
import tradingbot.domain.market.RawPayload;
import tradingbot.domain.market.StreamMarketDataEvent;
import tradingbot.domain.market.StreamMarketDataEvent.EventType;

class MarketDataSanitizerTest {

    private MarketDataSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new MarketDataSanitizer();
        ReflectionTestUtils.setField(sanitizer, "maxSpreadPercent", 5.0);
    }

    // -----------------------------------------------------------------------
    // Helper builders
    // -----------------------------------------------------------------------

    private static StreamMarketDataEvent tradeEvent(BigDecimal price, BigDecimal qty) {
        return new StreamMarketDataEvent(
                "BINANCE_FUTURES", "BTCUSDT", EventType.TRADE,
                price, qty, Instant.now(), new RawPayload("{}"));
    }

    private static StreamMarketDataEvent bookTickerEvent(BigDecimal bid, BigDecimal ask) {
        return new StreamMarketDataEvent(
                "BINANCE_FUTURES", "BTCUSDT", EventType.BOOK_TICKER,
                ask, BigDecimal.ZERO, Instant.now(),
                new BookTickerPayload(bid, ask));
    }

    private static StreamMarketDataEvent emptyPayloadEvent() {
        return new StreamMarketDataEvent(
                "BINANCE_FUTURES", "BTCUSDT", EventType.KLINE,
                BigDecimal.ONE, BigDecimal.ONE, Instant.now(),
                new EmptyPayload());
    }

    // =======================================================================
    // isValid
    // =======================================================================

    @Nested
    class IsValid {

        // ---- structural field checks ----

        @Test
        void rejectsNullExchange() {
            var event = new StreamMarketDataEvent(
                    null, "BTCUSDT", EventType.TRADE,
                    BigDecimal.ONE, BigDecimal.ONE, Instant.now(), new RawPayload("{}"));
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void rejectsBlankExchange() {
            var event = new StreamMarketDataEvent(
                    "  ", "BTCUSDT", EventType.TRADE,
                    BigDecimal.ONE, BigDecimal.ONE, Instant.now(), new RawPayload("{}"));
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void rejectsNullSymbol() {
            var event = new StreamMarketDataEvent(
                    "BINANCE_FUTURES", null, EventType.TRADE,
                    BigDecimal.ONE, BigDecimal.ONE, Instant.now(), new RawPayload("{}"));
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void rejectsNullEventType() {
            var event = new StreamMarketDataEvent(
                    "BINANCE_FUTURES", "BTCUSDT", null,
                    BigDecimal.ONE, BigDecimal.ONE, Instant.now(), new RawPayload("{}"));
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void rejectsNullPrice() {
            var event = new StreamMarketDataEvent(
                    "BINANCE_FUTURES", "BTCUSDT", EventType.TRADE,
                    null, BigDecimal.ONE, Instant.now(), new RawPayload("{}"));
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void rejectsZeroPrice() {
            var event = tradeEvent(BigDecimal.ZERO, BigDecimal.ONE);
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void rejectsNegativePrice() {
            var event = tradeEvent(new BigDecimal("-1"), BigDecimal.ONE);
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void rejectsNullTimestamp() {
            var event = new StreamMarketDataEvent(
                    "BINANCE_FUTURES", "BTCUSDT", EventType.TRADE,
                    BigDecimal.ONE, BigDecimal.ONE, null, new RawPayload("{}"));
            assertFalse(sanitizer.isValid(event));
        }

        // ---- TRADE quantity checks ----

        @Test
        void rejectsTradeWithZeroQuantity() {
            var event = tradeEvent(BigDecimal.ONE, BigDecimal.ZERO);
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void rejectsTradeWithNullQuantity() {
            var event = new StreamMarketDataEvent(
                    "BINANCE_FUTURES", "BTCUSDT", EventType.TRADE,
                    BigDecimal.ONE, null, Instant.now(), new RawPayload("{}"));
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void rejectsTradeWithNegativeQuantity() {
            var event = tradeEvent(BigDecimal.ONE, new BigDecimal("-0.5"));
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void acceptsValidTrade() {
            var event = tradeEvent(new BigDecimal("45000"), new BigDecimal("0.01"));
            assertTrue(sanitizer.isValid(event));
        }

        // ---- BOOK_TICKER spread checks ----

        @Test
        void acceptsNormalBookTicker() {
            var event = bookTickerEvent(new BigDecimal("44999"), new BigDecimal("45001"));
            assertTrue(sanitizer.isValid(event));
        }

        @Test
        void rejectsBookTickerWithZeroBid() {
            var event = bookTickerEvent(BigDecimal.ZERO, new BigDecimal("45000"));
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void rejectsBookTickerWithZeroAsk() {
            var event = bookTickerEvent(new BigDecimal("45000"), BigDecimal.ZERO);
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void rejectsBookTickerWithNegativeBid() {
            var event = bookTickerEvent(new BigDecimal("-1"), new BigDecimal("45000"));
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void rejectsWideSpread() {
            // 10% spread exceeds the 5% default threshold
            var event = bookTickerEvent(new BigDecimal("40500"), new BigDecimal("45000"));
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void acceptsSpreadAtExactThreshold() {
            // 5% spread: bid = 950, ask = 1000 → (1000-950)/1000*100 = 5.0
            var event = bookTickerEvent(new BigDecimal("950"), new BigDecimal("1000"));
            assertTrue(sanitizer.isValid(event));
        }

        @Test
        void rejectsSpreadJustOverThreshold() {
            // Slightly over 5%: bid = 949, ask = 1000 → 5.1%
            var event = bookTickerEvent(new BigDecimal("949"), new BigDecimal("1000"));
            assertFalse(sanitizer.isValid(event));
        }

        // ---- Payload-type pass-through ----

        @Test
        void acceptsRawPayload() {
            var event = tradeEvent(new BigDecimal("45000"), new BigDecimal("0.01"));
            assertTrue(sanitizer.isValid(event));
        }

        @Test
        void acceptsEmptyPayload() {
            var event = emptyPayloadEvent();
            assertTrue(sanitizer.isValid(event));
        }

        // ---- BOOK_TICKER zero quantity is acceptable ----

        @Test
        void acceptsBookTickerWithZeroQuantity() {
            // Book ticker events use BigDecimal.ZERO quantity — that's expected
            var event = bookTickerEvent(new BigDecimal("44999"), new BigDecimal("45001"));
            assertEquals(BigDecimal.ZERO, event.quantity());
            assertTrue(sanitizer.isValid(event));
        }
    }

    // =======================================================================
    // sanitize
    // =======================================================================

    @Nested
    class Sanitize {

        @Test
        void returnsEventUnchangedForNormalSpread() {
            var event = bookTickerEvent(new BigDecimal("44999"), new BigDecimal("45001"));
            var result = sanitizer.sanitize(event);
            assertSame(event, result);
        }

        @Test
        void correctsCrossedBook() {
            // bid > ask (crossed)
            var event = bookTickerEvent(new BigDecimal("45001"), new BigDecimal("44999"));
            var result = sanitizer.sanitize(event);

            assertNotSame(event, result);
            BookTickerPayload corrected = (BookTickerPayload) result.payload();
            assertEquals(new BigDecimal("44999"), corrected.bid());
            assertEquals(new BigDecimal("45001"), corrected.ask());
        }

        @Test
        void preservesEventFieldsWhenCorrecting() {
            var event = bookTickerEvent(new BigDecimal("45001"), new BigDecimal("44999"));
            var result = sanitizer.sanitize(event);

            assertEquals(event.exchange(), result.exchange());
            assertEquals(event.symbol(), result.symbol());
            assertEquals(event.type(), result.type());
            assertEquals(event.price(), result.price());
            assertEquals(event.quantity(), result.quantity());
            assertEquals(event.timestamp(), result.timestamp());
        }

        @Test
        void returnsEventUnchangedForEqualBidAsk() {
            var event = bookTickerEvent(new BigDecimal("45000"), new BigDecimal("45000"));
            var result = sanitizer.sanitize(event);
            assertSame(event, result);
        }

        @Test
        void passesRawPayloadThrough() {
            var event = tradeEvent(new BigDecimal("45000"), BigDecimal.ONE);
            var result = sanitizer.sanitize(event);
            assertSame(event, result);
        }

        @Test
        void passesEmptyPayloadThrough() {
            var event = emptyPayloadEvent();
            var result = sanitizer.sanitize(event);
            assertSame(event, result);
        }
    }

    // =======================================================================
    // configurable threshold
    // =======================================================================

    @Nested
    class ConfigurableThreshold {

        @Test
        void tighterThresholdRejectsModerateSpread() {
            ReflectionTestUtils.setField(sanitizer, "maxSpreadPercent", 0.5);
            // 1% spread — acceptable at 5% default, rejected at 0.5%
            var event = bookTickerEvent(new BigDecimal("990"), new BigDecimal("1000"));
            assertFalse(sanitizer.isValid(event));
        }

        @Test
        void looserThresholdAcceptsWideSpread() {
            ReflectionTestUtils.setField(sanitizer, "maxSpreadPercent", 20.0);
            // 10% spread — rejected at 5% default, accepted at 20%
            var event = bookTickerEvent(new BigDecimal("40500"), new BigDecimal("45000"));
            assertTrue(sanitizer.isValid(event));
        }
    }
}
