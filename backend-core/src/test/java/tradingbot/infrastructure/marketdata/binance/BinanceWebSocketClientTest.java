package tradingbot.infrastructure.marketdata.binance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import tradingbot.domain.market.KlineClosedEvent;

/**
 * Unit tests for {@link BinanceWebSocketClient} message-parsing logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BinanceWebSocketClient Unit Tests")
class BinanceWebSocketClientTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    CircuitBreakerRegistry circuitBreakerRegistry;

    BinanceWebSocketClient client;

    @BeforeEach
    void setUp() {
        client = new BinanceWebSocketClient(kafkaTemplate, circuitBreakerRegistry);
        // Inject a no-op wsClient stub so @PostConstruct is not required in tests
        ReflectionTestUtils.setField(client, "useTestnet", false);
    }

    // -------------------------------------------------------------------------
    // handleKlineMessage — closed candle (k.x = true)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Publishes KlineClosedEvent to correct Kafka topic when candle is closed")
    void handleKlineMessage_publishesEventWhenCandleClosed() throws Exception {
        String raw = closedKlineJson("BTCUSDT", "1m");

        client.handleKlineMessage(raw, "BTCUSDT", "1m");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("kline-closed.BTCUSDT"), eq("BTCUSDT"), payloadCaptor.capture());

        KlineClosedEvent event = (KlineClosedEvent) payloadCaptor.getValue();
        assertThat(event.exchange()).isEqualTo("BINANCE");
        assertThat(event.symbol()).isEqualTo("BTCUSDT");
        assertThat(event.interval()).isEqualTo("1m");
        assertThat(event.open()).isEqualByComparingTo(new BigDecimal("29000.00"));
        assertThat(event.high()).isEqualByComparingTo(new BigDecimal("29500.00"));
        assertThat(event.low()).isEqualByComparingTo(new BigDecimal("28900.00"));
        assertThat(event.close()).isEqualByComparingTo(new BigDecimal("29400.00"));
        assertThat(event.volume()).isEqualByComparingTo(new BigDecimal("100.5"));
    }

    @Test
    @DisplayName("Does NOT publish when candle is still open (k.x = false)")
    void handleKlineMessage_skipsOpenCandle() throws Exception {
        String raw = openKlineJson("BTCUSDT", "1m");

        client.handleKlineMessage(raw, "BTCUSDT", "1m");

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Does NOT publish when JSON has no 'k' field")
    void handleKlineMessage_skipsMessageWithoutKlineNode() throws Exception {
        String raw = "{\"e\":\"kline\",\"symbol\":\"BTCUSDT\"}";

        client.handleKlineMessage(raw, "BTCUSDT", "1m");

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Symbol is uppercased in event and topic regardless of input case")
    void handleKlineMessage_normalizesSymbolToUpperCase() throws Exception {
        String raw = closedKlineJson("btcusdt", "1m");

        client.handleKlineMessage(raw, "btcusdt", "1m");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("kline-closed.BTCUSDT"), eq("BTCUSDT"), payloadCaptor.capture());
        KlineClosedEvent event = (KlineClosedEvent) payloadCaptor.getValue();
        assertThat(event.symbol()).isEqualTo("BTCUSDT");
    }

    // -------------------------------------------------------------------------
    // subscribeKlineStream — idempotency
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("subscribeKlineStream is idempotent — duplicate call for same symbol/interval is a no-op")
    void subscribeKlineStream_isIdempotent() {
        // Inject a mock wsClient so the real connection attempt is skipped
        com.binance.connector.futures.client.impl.UMWebsocketClientImpl mockWsClient =
                mock(com.binance.connector.futures.client.impl.UMWebsocketClientImpl.class);
        io.github.resilience4j.circuitbreaker.CircuitBreaker mockCb =
                mock(io.github.resilience4j.circuitbreaker.CircuitBreaker.class);
        org.mockito.Mockito.when(circuitBreakerRegistry.circuitBreaker(
                BinanceWebSocketClient.CIRCUIT_BREAKER_NAME)).thenReturn(mockCb);
        org.mockito.Mockito.when(mockCb.tryAcquirePermission()).thenReturn(true);
        org.mockito.Mockito.when(mockWsClient.klineStream(any(), any(), any())).thenReturn(1);
        ReflectionTestUtils.setField(client, "wsClient", mockWsClient);

        client.subscribeKlineStream("BTCUSDT", "1m");
        client.subscribeKlineStream("BTCUSDT", "1m"); // duplicate — should be ignored

        verify(mockWsClient, org.mockito.Mockito.times(1)).klineStream(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String closedKlineJson(String symbol, String interval) {
        return """
                {
                  "e": "kline",
                  "E": 1700000060000,
                  "s": "%s",
                  "k": {
                    "t": 1700000000000,
                    "T": 1700000059999,
                    "s": "%s",
                    "i": "%s",
                    "o": "29000.00",
                    "h": "29500.00",
                    "l": "28900.00",
                    "c": "29400.00",
                    "v": "100.5",
                    "x": true
                  }
                }
                """.formatted(symbol, symbol, interval);
    }

    private static String openKlineJson(String symbol, String interval) {
        return """
                {
                  "e": "kline",
                  "E": 1700000060000,
                  "s": "%s",
                  "k": {
                    "t": 1700000000000,
                    "T": 1700000059999,
                    "s": "%s",
                    "i": "%s",
                    "o": "29000.00",
                    "h": "29500.00",
                    "l": "28900.00",
                    "c": "29400.00",
                    "v": "100.5",
                    "x": false
                  }
                }
                """.formatted(symbol, symbol, interval);
    }
}
