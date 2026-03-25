package tradingbot.infrastructure.marketdata.bybit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bybit.api.client.service.BybitApiClientFactory;
import com.bybit.api.client.websocket.httpclient.WebsocketStreamClient;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tradingbot.domain.market.BookTickerPayload;
import tradingbot.domain.market.EmptyPayload;
import tradingbot.domain.market.RawPayload;
import tradingbot.domain.market.StreamMarketDataEvent;
import tradingbot.domain.market.StreamMarketDataEvent.EventType;
import tradingbot.infrastructure.marketdata.ExchangeWebSocketClient;

/**
 * Reactive WebSocket client for Bybit V5 (Linear) using official SDK wrapper.
 * Uses Sinks.many().replay().limit(1000) for hot observable sharing.
 *
 * <p>Message parsing uses Jackson data-binding ({@link BybitEnvelope},
 * {@link BybitTrade}, {@link BybitOrderBook}) to eliminate manual JsonNode
 * traversal. Control frames (op=subscribe/pong/auth) are logged and discarded.
 */
@Service
public class BybitWebSocketAdapter implements ExchangeWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(BybitWebSocketAdapter.class);

    @Override
    public String getExchangeName() {
        return "BYBIT_LINEAR";
    }

    private final Map<String, Sinks.Many<StreamMarketDataEvent>> tradeStreams  = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<StreamMarketDataEvent>> tickerStreams = new ConcurrentHashMap<>();

    private WebsocketStreamClient wsClient;

    /** Spring-managed ObjectMapper — includes JavaTimeModule and custom modules. */
    private final ObjectMapper objectMapper;
    private final boolean useTestnet;

    public BybitWebSocketAdapter(
            ObjectMapper objectMapper,
            @Value("${exchange.bybit.use-testnet:false}") boolean useTestnet) {
        this.objectMapper = objectMapper;
        this.useTestnet   = useTestnet;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing Bybit WebSocket Client [testnet={}]", useTestnet);
        this.wsClient = BybitApiClientFactory
                .newInstance("BybitLinear", useTestnet)
                .newWebsocketClient();
        this.wsClient.setMessageHandler(this::handleMessage);
    }

    // -------------------------------------------------------------------------
    // Message handling
    // -------------------------------------------------------------------------

    private void handleMessage(String message) {
        try {
            BybitEnvelope envelope = objectMapper.readValue(message, BybitEnvelope.class);

            // Control frames (subscribe confirm, pong, auth) — no topic field
            if (envelope.topic() == null) {
                if (envelope.op() != null) {
                    log.debug("Bybit control frame [op={}] success={}", envelope.op(), envelope.success());
                    if (Boolean.FALSE.equals(envelope.success())) {
                        log.warn("Bybit subscription/auth failed: {}", message);
                    }
                }
                return;
            }

            if (envelope.data() == null) return;

            String[] parts = envelope.topic().split("\\.");
            if (parts.length < 2) {
                log.warn("Unexpected Bybit topic format: {}", envelope.topic());
                return;
            }

            // Symbol is always the last segment: publicTrade.BTCUSDT / orderbook.1.BTCUSDT
            String symbol = parts[parts.length - 1];
            long   ts     = envelope.ts() != null ? envelope.ts() : System.currentTimeMillis();

            if (envelope.ts() == null) {
                log.warn("Missing ts in Bybit message for topic {}", envelope.topic());
            }

            if (envelope.topic().startsWith("publicTrade.")) {
                Sinks.Many<StreamMarketDataEvent> sink = tradeStreams.get(symbol);
                if (sink != null) {
                    BybitTrade[] trades = objectMapper.treeToValue(envelope.data(), BybitTrade[].class);
                    parseTrades(trades, symbol, ts, sink);
                }
            } else if (envelope.topic().startsWith("orderbook.")) {
                Sinks.Many<StreamMarketDataEvent> sink = tickerStreams.get(symbol);
                if (sink != null) {
                    BybitOrderBook book = objectMapper.treeToValue(envelope.data(), BybitOrderBook.class);
                    parseOrderBook(book, symbol, ts, sink);
                }
            }

        } catch (Exception e) {
            log.error("Error handling Bybit WS message: {}", message, e);
        }
    }

    private void parseTrades(BybitTrade[] trades, String symbol, long ts,
                              Sinks.Many<StreamMarketDataEvent> sink) {
        for (BybitTrade trade : trades) {
            long eventTime = trade.time() != null ? trade.time() : ts;
            StreamMarketDataEvent event = new StreamMarketDataEvent(
                    "BYBIT_LINEAR",
                    symbol,
                    EventType.TRADE,
                    trade.price(),
                    trade.volume(),
                    Instant.ofEpochMilli(eventTime),
                    log.isDebugEnabled() ? new RawPayload(trade.toString()) : new EmptyPayload()
            );
            emitSafely(sink, symbol, event);
        }
    }

    private void parseOrderBook(BybitOrderBook book, String symbol, long ts,
                                 Sinks.Many<StreamMarketDataEvent> sink) {
        BigDecimal bestAsk = extractFirst(book.asks());
        BigDecimal bestBid = extractFirst(book.bids());

        if (bestAsk.signum() > 0 && bestBid.signum() > 0) {
            StreamMarketDataEvent event = new StreamMarketDataEvent(
                    "BYBIT_LINEAR",
                    symbol,
                    EventType.BOOK_TICKER,
                    bestAsk,
                    BigDecimal.ZERO,
                    Instant.ofEpochMilli(ts),
                    new BookTickerPayload(bestBid, bestAsk)
            );
            emitSafely(sink, symbol, event);
        }
    }

    /**
     * Emits an event and logs a warning if the sink is in a terminal/full state.
     * Removes the sink from the map on terminal failure so the next
     * {@link #streamTrades}/{@link #streamBookTicker} call re-subscribes cleanly.
     */
    private void emitSafely(Sinks.Many<StreamMarketDataEvent> sink,
                             String symbol,
                             StreamMarketDataEvent event) {
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            log.warn("Failed to emit event for symbol {} — result={}", symbol, result);
            if (result == Sinks.EmitResult.FAIL_TERMINATED) {
                tradeStreams.remove(symbol);
                tickerStreams.remove(symbol);
            }
        }
    }

    /** Safely extracts the first price from a Bybit asks/bids list-of-lists. */
    private BigDecimal extractFirst(List<List<String>> levels) {
        if (levels != null && !levels.isEmpty()) {
            List<String> best = levels.get(0);
            if (best != null && !best.isEmpty()) {
                return new BigDecimal(best.get(0));
            }
        }
        return BigDecimal.ZERO;
    }

    // -------------------------------------------------------------------------
    // Public stream API
    // -------------------------------------------------------------------------

    @Override
    public Flux<StreamMarketDataEvent> streamTrades(String symbol) {
        return tradeStreams.computeIfAbsent(symbol, s -> {
            Sinks.Many<StreamMarketDataEvent> sink = Sinks.many().replay().limit(1000);
            wsClient.getPublicChannelStream(List.of("publicTrade." + s), "subscribe");
            return sink;
        }).asFlux();
    }

    @Override
    public Flux<StreamMarketDataEvent> streamBookTicker(String symbol) {
        return tickerStreams.computeIfAbsent(symbol, s -> {
            Sinks.Many<StreamMarketDataEvent> sink = Sinks.many().replay().limit(1000);
            wsClient.getPublicChannelStream(List.of("orderbook.1." + s), "subscribe");
            return sink;
        }).asFlux();
    }

    @PreDestroy
    public void cleanup() {
        log.info("BybitWebSocketAdapter shutting down");
        // SDK manages its own WS connection lifecycle.
        // Call wsClient.close() here if the SDK adds it in a future version.
    }

    // -------------------------------------------------------------------------
    // Jackson DTOs — private, scoped to this adapter
    // -------------------------------------------------------------------------

    /** Outer envelope for every Bybit V5 WebSocket message. */
    private record BybitEnvelope(
            String   topic,
            String   op,
            Boolean  success,
            Long     ts,
            JsonNode data        // kept as JsonNode for topic-specific routing
    ) {}

    /**
     * Single trade entry from a {@code publicTrade.*} message.
     * Field names match Bybit V5 compressed keys.
     */
    private record BybitTrade(
            @JsonProperty("p") BigDecimal price,
            @JsonProperty("v") BigDecimal volume,
            @JsonProperty("T") Long       time
    ) {}

    /**
     * Orderbook snapshot/delta payload from an {@code orderbook.*} message.
     * Each level is {@code [price, size]} as strings.
     */
    private record BybitOrderBook(
            @JsonProperty("a") List<List<String>> asks,
            @JsonProperty("b") List<List<String>> bids
    ) {}
}
