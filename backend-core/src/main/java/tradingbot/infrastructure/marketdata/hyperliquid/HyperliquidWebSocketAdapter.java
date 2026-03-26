package tradingbot.infrastructure.marketdata.hyperliquid;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * Reactive WebSocket client for Hyperliquid (mainnet and testnet).
 *
 * <p>Uses a single multiplexed connection. All subscriptions are tracked and
 * automatically re-issued on reconnect. Incoming messages are routed to
 * per-symbol {@link Sinks} by channel type.
 *
 * <p>Symbol convention: the rest of the codebase uses "BTCUSDT" style; Hyperliquid
 * uses coin names ("BTC"). The adapter converts at the boundary — callers always
 * pass "BTCUSDT", the wire always sends "BTC".
 */
@Service
public class HyperliquidWebSocketAdapter implements ExchangeWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(HyperliquidWebSocketAdapter.class);
    private static final String EXCHANGE = "HYPERLIQUID";

    private final ObjectMapper objectMapper;
    private final String wsUrl;

    private volatile WebSocket webSocket;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    // Sinks keyed by normalized symbol (e.g. "BTCUSDT")
    private final Map<String, Sinks.Many<StreamMarketDataEvent>> tradeStreams  = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<StreamMarketDataEvent>> tickerStreams = new ConcurrentHashMap<>();

    // Active subscription registry — re-issued on each reconnect
    private final Set<String> tradeSubs  = ConcurrentHashMap.newKeySet();
    private final Set<String> tickerSubs = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hl-ws-heartbeat");
        t.setDaemon(true);
        return t;
    });

    @Override
    public String getExchangeName() {
        return EXCHANGE;
    }

    public HyperliquidWebSocketAdapter(
            ObjectMapper objectMapper,
            @Value("${exchange.hyperliquid.use-testnet:true}") boolean useTestnet) {
        this.objectMapper = objectMapper;
        this.wsUrl = useTestnet
                ? "wss://api.hyperliquid-testnet.xyz/ws"
                : "wss://api.hyperliquid.xyz/ws";
    }

    @PostConstruct
    public void connect() {
        openConnection();
        scheduler.scheduleAtFixedRate(this::sendPing, 30, 30, TimeUnit.SECONDS);
    }

    private void openConnection() {
        WebSocket prev = webSocket;
        if (prev != null) {
            try { prev.abort(); } catch (Exception ignored) {}
        }
        webSocket = null;

        log.info("Connecting to Hyperliquid WebSocket [{}]", wsUrl);
        try {
            webSocket = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), new MessageListener())
                    .join();
            log.info("Hyperliquid WebSocket connected");
            resubscribeAll();
        } catch (Exception e) {
            log.error("Failed to connect to Hyperliquid WebSocket — retrying in 5s", e);
            scheduler.schedule(this::openConnection, 5, TimeUnit.SECONDS);
        }
    }

    private void resubscribeAll() {
        tradeSubs.forEach(symbol  -> sendSubscribe("trades", toCoin(symbol)));
        tickerSubs.forEach(symbol -> sendSubscribe("l2Book", toCoin(symbol)));
    }

    private void sendPing() {
        WebSocket ws = webSocket;
        if (ws != null && !ws.isOutputClosed()) {
            ws.sendText("{\"method\":\"ping\"}", true);
        }
    }

    private void sendSubscribe(String type, String coin) {
        WebSocket ws = webSocket;
        if (ws == null || ws.isOutputClosed()) return;
        String msg = String.format(
                "{\"method\":\"subscribe\",\"subscription\":{\"type\":\"%s\",\"coin\":\"%s\"}}",
                type, coin);
        ws.sendText(msg, true);
        log.debug("Hyperliquid subscribed [type={}, coin={}]", type, coin);
    }

    // -------------------------------------------------------------------------
    // Message handling
    // -------------------------------------------------------------------------

    void onMessage(String raw) {
        try {
            HlEnvelope env = objectMapper.readValue(raw, HlEnvelope.class);
            String channel = env.channel();
            if (channel == null || "pong".equals(channel)) return;

            if ("trades".equals(channel) && env.data() != null) {
                handleTrades(env.data());
            } else if ("l2Book".equals(channel) && env.data() != null) {
                handleL2Book(env.data());
            }
        } catch (Exception e) {
            log.error("Error parsing Hyperliquid WS message: {}", raw, e);
        }
    }

    private void handleTrades(JsonNode dataArray) throws Exception {
        if (!dataArray.isArray()) return;
        for (JsonNode node : dataArray) {
            HlTrade trade = objectMapper.treeToValue(node, HlTrade.class);
            String symbol = toSymbol(trade.coin());
            Sinks.Many<StreamMarketDataEvent> sink = tradeStreams.get(symbol);
            if (sink == null) continue;
            long ts = trade.time() > 0 ? trade.time() : System.currentTimeMillis();
            StreamMarketDataEvent event = new StreamMarketDataEvent(
                    EXCHANGE, symbol, EventType.TRADE,
                    new BigDecimal(trade.px()),
                    new BigDecimal(trade.sz()),
                    Instant.ofEpochMilli(ts),
                    log.isDebugEnabled() ? new RawPayload(node.toString()) : new EmptyPayload()
            );
            emitSafely(sink, symbol, event, tradeStreams);
        }
    }

    private void handleL2Book(JsonNode data) throws Exception {
        HlL2Book book = objectMapper.treeToValue(data, HlL2Book.class);
        if (book.levels() == null || book.levels().size() < 2) return;

        String symbol = toSymbol(book.coin());
        Sinks.Many<StreamMarketDataEvent> sink = tickerStreams.get(symbol);
        if (sink == null) return;

        List<HlLevel> asks = book.levels().get(0);
        List<HlLevel> bids = book.levels().get(1);
        if (asks.isEmpty() || bids.isEmpty()) return;

        BigDecimal bestAsk = new BigDecimal(asks.get(0).px());
        BigDecimal bestBid = new BigDecimal(bids.get(0).px());
        long ts = book.time() > 0 ? book.time() : System.currentTimeMillis();

        StreamMarketDataEvent event = new StreamMarketDataEvent(
                EXCHANGE, symbol, EventType.BOOK_TICKER,
                bestAsk, BigDecimal.ZERO,
                Instant.ofEpochMilli(ts),
                new BookTickerPayload(bestBid, bestAsk)
        );
        emitSafely(sink, symbol, event, tickerStreams);
    }

    private void emitSafely(Sinks.Many<StreamMarketDataEvent> sink, String symbol,
                             StreamMarketDataEvent event,
                             Map<String, Sinks.Many<StreamMarketDataEvent>> sinks) {
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            log.warn("Failed to emit event for {} — result={}", symbol, result);
            if (result == Sinks.EmitResult.FAIL_TERMINATED) {
                sinks.remove(symbol);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Public stream API
    // -------------------------------------------------------------------------

    @Override
    public Flux<StreamMarketDataEvent> streamTrades(String symbol) {
        return tradeStreams.computeIfAbsent(symbol, s -> {
            Sinks.Many<StreamMarketDataEvent> sink = Sinks.many().replay().limit(1000);
            tradeSubs.add(s);
            sendSubscribe("trades", toCoin(s));
            return sink;
        }).asFlux();
    }

    @Override
    public Flux<StreamMarketDataEvent> streamBookTicker(String symbol) {
        return tickerStreams.computeIfAbsent(symbol, s -> {
            Sinks.Many<StreamMarketDataEvent> sink = Sinks.many().replay().limit(1000);
            tickerSubs.add(s);
            sendSubscribe("l2Book", toCoin(s));
            return sink;
        }).asFlux();
    }

    @PreDestroy
    public void cleanup() {
        log.info("Closing Hyperliquid WebSocket");
        scheduler.shutdownNow();
        WebSocket ws = webSocket;
        if (ws != null) ws.abort();
    }

    // -------------------------------------------------------------------------
    // Symbol conversion
    // -------------------------------------------------------------------------

    /** "BTCUSDT" → "BTC" */
    private static String toCoin(String symbol) {
        return symbol.endsWith("USDT") ? symbol.substring(0, symbol.length() - 4) : symbol;
    }

    /** "BTC" → "BTCUSDT" */
    private static String toSymbol(String coin) {
        return coin + "USDT";
    }

    // -------------------------------------------------------------------------
    // WebSocket Listener
    // -------------------------------------------------------------------------

    private class MessageListener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                onMessage(buffer.toString());
                buffer.setLength(0);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.warn("Hyperliquid WS closed [status={}, reason={}] — reconnecting in 3s",
                    statusCode, reason);
            scheduler.schedule(HyperliquidWebSocketAdapter.this::openConnection, 3, TimeUnit.SECONDS);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.error("Hyperliquid WS error — reconnecting in 3s", error);
            scheduler.schedule(HyperliquidWebSocketAdapter.this::openConnection, 3, TimeUnit.SECONDS);
        }
    }

    // -------------------------------------------------------------------------
    // Jackson DTOs — private, scoped to this adapter
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HlEnvelope(String channel, JsonNode data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HlTrade(String coin, String px, String sz, long time) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HlL2Book(String coin, long time, List<List<HlLevel>> levels) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HlLevel(String px, String sz) {}
}
