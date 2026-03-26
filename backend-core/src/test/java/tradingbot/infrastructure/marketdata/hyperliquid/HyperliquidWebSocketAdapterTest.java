package tradingbot.infrastructure.marketdata.hyperliquid;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tradingbot.domain.market.BookTickerPayload;
import tradingbot.domain.market.StreamMarketDataEvent;
import tradingbot.domain.market.StreamMarketDataEvent.EventType;

/**
 * Unit tests for {@link HyperliquidWebSocketAdapter} message parsing.
 *
 * <p>Uses a test-only subclass that exposes {@code injectMessage()} so the
 * parsing path can be exercised without opening a real WebSocket connection.
 */
@DisplayName("HyperliquidWebSocketAdapter — message parsing")
class HyperliquidWebSocketAdapterTest {

    private TestableAdapter adapter;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        adapter = new TestableAdapter(mapper);
    }

    // -------------------------------------------------------------------------
    // Trades channel
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("trades message emits TRADE event with correct price, size and symbol")
    void tradeMessage_emitsTradeEvent() {
        Flux<StreamMarketDataEvent> stream = adapter.streamTrades("BTCUSDT");

        String msg = """
                {"channel":"trades","data":[
                  {"coin":"BTC","side":"B","px":"65000.5","sz":"0.001","time":1710000000000}
                ]}
                """;

        StepVerifier.create(stream.take(1))
                .then(() -> adapter.injectMessage(msg))
                .assertNext(event -> {
                    assertThat(event.exchange()).isEqualTo("HYPERLIQUID");
                    assertThat(event.symbol()).isEqualTo("BTCUSDT");
                    assertThat(event.type()).isEqualTo(EventType.TRADE);
                    assertThat(event.price()).isEqualByComparingTo("65000.5");
                    assertThat(event.quantity()).isEqualByComparingTo("0.001");
                    assertThat(event.timestamp().toEpochMilli()).isEqualTo(1710000000000L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("trades message with multiple trades emits one event per trade")
    void tradeMessage_multipleEntries_emitsMultipleEvents() {
        Flux<StreamMarketDataEvent> stream = adapter.streamTrades("ETHUSDT");

        String msg = """
                {"channel":"trades","data":[
                  {"coin":"ETH","side":"A","px":"3500.0","sz":"0.5","time":1710000001000},
                  {"coin":"ETH","side":"B","px":"3501.0","sz":"1.0","time":1710000002000}
                ]}
                """;

        StepVerifier.create(stream.take(2))
                .then(() -> adapter.injectMessage(msg))
                .assertNext(e -> assertThat(e.price()).isEqualByComparingTo("3500.0"))
                .assertNext(e -> assertThat(e.price()).isEqualByComparingTo("3501.0"))
                .verifyComplete();
    }

    @Test
    @DisplayName("trade for unsubscribed symbol is silently ignored")
    void tradeMessage_unknownSymbol_ignored() {
        // Subscribe to BTC but receive ETH message
        Flux<StreamMarketDataEvent> btcStream = adapter.streamTrades("BTCUSDT");

        String msg = """
                {"channel":"trades","data":[
                  {"coin":"ETH","side":"B","px":"3500.0","sz":"0.1","time":1710000000000}
                ]}
                """;

        StepVerifier.create(btcStream.take(1).timeout(
                java.time.Duration.ofMillis(200), Flux.empty()))
                .then(() -> adapter.injectMessage(msg))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // l2Book channel
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("l2Book message emits BOOK_TICKER event with best bid and ask")
    void l2BookMessage_emitsBookTickerEvent() {
        Flux<StreamMarketDataEvent> stream = adapter.streamBookTicker("BTCUSDT");

        String msg = """
                {"channel":"l2Book","data":{
                  "coin":"BTC","time":1710000000000,
                  "levels":[
                    [{"px":"65001.0","sz":"0.5","n":3},{"px":"65002.0","sz":"0.3","n":1}],
                    [{"px":"64999.0","sz":"0.4","n":2},{"px":"64998.0","sz":"0.6","n":4}]
                  ]
                }}
                """;

        StepVerifier.create(stream.take(1))
                .then(() -> adapter.injectMessage(msg))
                .assertNext(event -> {
                    assertThat(event.exchange()).isEqualTo("HYPERLIQUID");
                    assertThat(event.symbol()).isEqualTo("BTCUSDT");
                    assertThat(event.type()).isEqualTo(EventType.BOOK_TICKER);
                    assertThat(event.price()).isEqualByComparingTo("65001.0"); // best ask
                    BookTickerPayload btp = (BookTickerPayload) event.payload();
                    assertThat(btp.ask()).isEqualByComparingTo("65001.0");
                    assertThat(btp.bid()).isEqualByComparingTo("64999.0");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("l2Book with empty levels is safely ignored")
    void l2BookMessage_emptyLevels_ignored() {
        Flux<StreamMarketDataEvent> stream = adapter.streamBookTicker("BTCUSDT");

        String msg = """
                {"channel":"l2Book","data":{
                  "coin":"BTC","time":1710000000000,
                  "levels":[[],[]]
                }}
                """;

        StepVerifier.create(stream.take(1).timeout(
                java.time.Duration.ofMillis(200), Flux.empty()))
                .then(() -> adapter.injectMessage(msg))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Control frames
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("pong message is silently discarded")
    void pongMessage_discarded() {
        Flux<StreamMarketDataEvent> stream = adapter.streamTrades("BTCUSDT");

        StepVerifier.create(stream.take(1).timeout(
                java.time.Duration.ofMillis(200), Flux.empty()))
                .then(() -> adapter.injectMessage("{\"channel\":\"pong\"}"))
                .verifyComplete();
    }

    @Test
    @DisplayName("malformed JSON is logged and does not crash the adapter")
    void malformedJson_doesNotCrash() {
        Flux<StreamMarketDataEvent> stream = adapter.streamTrades("BTCUSDT");

        StepVerifier.create(stream.take(1).timeout(
                java.time.Duration.ofMillis(200), Flux.empty()))
                .then(() -> adapter.injectMessage("not-json{"))
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // getExchangeName
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getExchangeName returns HYPERLIQUID")
    void getExchangeName_returnsHyperliquid() {
        assertThat(adapter.getExchangeName()).isEqualTo("HYPERLIQUID");
    }

    // -------------------------------------------------------------------------
    // Test double — bypasses WebSocket connection
    // -------------------------------------------------------------------------

    /**
     * Subclass that overrides {@link #connect()} so no real WebSocket is opened,
     * and exposes {@link #injectMessage(String)} for test-driven message delivery.
     */
    static class TestableAdapter extends HyperliquidWebSocketAdapter {

        TestableAdapter(ObjectMapper objectMapper) {
            super(objectMapper, true);  // testnet=true, but connection is skipped
        }

        @Override
        public void connect() {
            // intentionally no-op — no real WebSocket in unit tests
        }

        void injectMessage(String message) {
            // Call the package-private message handler via reflection to keep
            // HyperliquidWebSocketAdapter's internal method private.
            // We use the same effect by invoking onMessage via the overridden path.
            onMessageForTest(message);
        }

        // Exposed hook — see HyperliquidWebSocketAdapter.onMessage (package-private for tests)
        void onMessageForTest(String raw) {
            super.onMessage(raw);
        }
    }
}
