package tradingbot.infrastructure.marketdata.binance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.binance.connector.futures.client.impl.UMWebsocketClientImpl;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import tradingbot.domain.market.KlineClosedEvent;

/**
 * Binance Futures WebSocket client for kline (candlestick) streams.
 *
 * <p>Connects to {@code wss://fstream.binance.com/ws/<symbol>@kline_<interval>},
 * parses each incoming JSON frame, and publishes a {@link KlineClosedEvent} to the
 * Kafka topic {@code kline-closed.<SYMBOL>} whenever the {@code k.x} flag is {@code true}
 * (meaning the candle is definitively closed).
 *
 * <p>Resilience strategy:
 * <ul>
 *   <li>A named Resilience4j {@link CircuitBreaker} ({@code binance-ws-kline}) gates every
 *       connection attempt; once the circuit opens, reconnection is suspended until the
 *       configured wait duration elapses.</li>
 *   <li>On any connection error the client schedules an exponential-backoff reconnect
 *       (capped at 60 s) on a single-threaded daemon executor.</li>
 * </ul>
 */
@Service
public class BinanceWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketClient.class);

    static final String CIRCUIT_BREAKER_NAME = "binance-ws-kline";

    private static final String PROD_URL = "wss://fstream.binance.com";
    private static final String TESTNET_URL = "wss://stream.binancefuture.com";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ScheduledExecutorService reconnectExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "binance-kline-ws-reconnect");
                t.setDaemon(true);
                return t;
            });

    /** Active Binance stream IDs, keyed by "symbol-interval". */
    private final Map<String, Integer> streamIds = new ConcurrentHashMap<>();

    /** Tracks which symbol/interval combinations are currently subscribed. */
    private final Set<String> activeSubscriptions = ConcurrentHashMap.newKeySet();

    private UMWebsocketClientImpl wsClient;

    @Value("${exchange.binance.use-testnet:false}")
    private boolean useTestnet;

    public BinanceWebSocketClient(
            KafkaTemplate<String, Object> kafkaTemplate,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @PostConstruct
    public void init() {
        String baseUrl = useTestnet ? TESTNET_URL : PROD_URL;
        log.info("Initializing Binance Futures kline WebSocket client [url={}, testnet={}]",
                baseUrl, useTestnet);
        wsClient = useTestnet
                ? new UMWebsocketClientImpl(TESTNET_URL)
                : new UMWebsocketClientImpl(PROD_URL);
    }

    /**
     * Subscribes to the kline stream for the given symbol and interval.
     *
     * <p>The subscription is idempotent: calling this method more than once for the
     * same symbol/interval pair is a no-op.
     *
     * @param symbol   trading pair in upper- or lower-case, e.g. {@code "BTCUSDT"}
     * @param interval kline interval string, e.g. {@code "1m"}, {@code "5m"}, {@code "1h"}
     */
    public void subscribeKlineStream(String symbol, String interval) {
        String key = symbol.toLowerCase() + "-" + interval;
        if (!activeSubscriptions.add(key)) {
            log.debug("Kline stream already active for {}/{}", symbol, interval);
            return;
        }
        connectKlineStream(symbol, interval, 0);
    }

    // -------------------------------------------------------------------------
    // Internal connection & reconnect logic
    // -------------------------------------------------------------------------

    private void connectKlineStream(String symbol, String interval, int attempt) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);

        if (!circuitBreaker.tryAcquirePermission()) {
            log.warn("[CircuitBreaker] Circuit OPEN for {} — deferring reconnect for {}/{}",
                    CIRCUIT_BREAKER_NAME, symbol, interval);
            scheduleReconnect(symbol, interval, attempt);
            return;
        }

        try {
            log.info("Connecting to Binance kline stream {}/{} (attempt {})", symbol, interval, attempt);
            Integer streamId = wsClient.klineStream(
                    symbol.toLowerCase(),
                    interval,
                    event -> {
                        try {
                            handleKlineMessage(event, symbol, interval);
                            circuitBreaker.onSuccess(0, TimeUnit.MILLISECONDS);
                        } catch (Exception e) {
                            log.error("Error processing kline message for {}/{}", symbol, interval, e);
                            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, e);
                        }
                    });

            streamIds.put(symbol.toLowerCase() + "-" + interval, streamId);
            log.info("Kline stream connected for {}/{} (streamId={})", symbol, interval, streamId);

        } catch (Exception e) {
            log.error("Failed to connect kline stream for {}/{} (attempt {})", symbol, interval, attempt, e);
            circuitBreaker.onError(0, TimeUnit.MILLISECONDS, e);
            scheduleReconnect(symbol, interval, attempt);
        }
    }

    private void scheduleReconnect(String symbol, String interval, int attempt) {
        long delaySeconds = Math.min(60L, 1L << Math.min(attempt, 6)); // 1, 2, 4, 8, 16, 32, 60 s
        log.info("Scheduling kline reconnect for {}/{} in {}s (attempt {})",
                symbol, interval, delaySeconds, attempt + 1);
        reconnectExecutor.schedule(
                () -> connectKlineStream(symbol, interval, attempt + 1),
                delaySeconds,
                TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------------------
    // Message parsing
    // -------------------------------------------------------------------------

    void handleKlineMessage(String rawMessage, String symbol, String interval) throws Exception {
        KlineStreamPayload payload = objectMapper.readValue(rawMessage, KlineStreamPayload.class);
        KlineData k = payload.k();
        if (k == null || !k.closed()) {
            // Candle not yet closed — ignore in-progress updates
            return;
        }

        KlineClosedEvent event = new KlineClosedEvent(
                "BINANCE",
                symbol.toUpperCase(),
                interval,
                new BigDecimal(k.open()),
                new BigDecimal(k.high()),
                new BigDecimal(k.low()),
                new BigDecimal(k.close()),
                new BigDecimal(k.volume()),
                Instant.ofEpochMilli(k.openTime()),
                Instant.ofEpochMilli(k.closeTime()));

        String topic = "kline-closed." + symbol.toUpperCase();
        kafkaTemplate.send(topic, symbol.toUpperCase(), event);
        log.debug("Published KlineClosedEvent to {} [close={}]", topic, event.close());
    }

    // -------------------------------------------------------------------------
    // Typed payloads for Binance kline stream deserialization
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KlineStreamPayload(@JsonProperty("k") KlineData k) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KlineData(
            @JsonProperty("o") String open,
            @JsonProperty("h") String high,
            @JsonProperty("l") String low,
            @JsonProperty("c") String close,
            @JsonProperty("v") String volume,
            @JsonProperty("t") long openTime,
            @JsonProperty("T") long closeTime,
            @JsonProperty("x") boolean closed) {}

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @PreDestroy
    public void cleanup() {
        log.info("Closing all Binance kline streams");
        streamIds.forEach((key, id) -> {
            try {
                wsClient.closeConnection(id);
            } catch (Exception e) {
                log.warn("Error closing stream {} (id={}): {}", key, id, e.getMessage());
            }
        });
        streamIds.clear();
        activeSubscriptions.clear();
        reconnectExecutor.shutdownNow();
    }
}
