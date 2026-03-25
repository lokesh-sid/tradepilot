package tradingbot.infrastructure.marketdata.binance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.binance.connector.futures.client.impl.UMWebsocketClientImpl;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tradingbot.domain.market.BookTickerPayload;
import tradingbot.domain.market.RawPayload;
import tradingbot.domain.market.StreamMarketDataEvent;
import tradingbot.domain.market.StreamMarketDataEvent.EventType;
import tradingbot.infrastructure.marketdata.ExchangeWebSocketClient;

/**
 * Reactive wrapper around Binance Futures WebSocket client.
 * Fully aligned with Phase 1 upgrade plan.
 * Uses Sinks.many().replay().limit(1000) for hot observable sharing.
 */
@Service
public class BinanceWebSocketAdapter implements ExchangeWebSocketClient {
    
    private static final Logger log = LoggerFactory.getLogger(BinanceWebSocketAdapter.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AggTradeEvent(
        @JsonProperty("p") BigDecimal price,
        @JsonProperty("q") BigDecimal qty,
        @JsonProperty("T") Long time
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BookTickerEvent(
        @JsonProperty("b") BigDecimal bidPrice,
        @JsonProperty("a") BigDecimal askPrice,
        @JsonProperty("T") Long time
    ) {}

    @Override
    public String getExchangeName() {
        return "BINANCE_FUTURES";
    }
    private UMWebsocketClientImpl wsClient; 
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Cache of active streams to avoid multiple WS connections for same symbol
    private final Map<String, Sinks.Many<StreamMarketDataEvent>> tradeStreams = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<StreamMarketDataEvent>> tickerStreams = new ConcurrentHashMap<>();
    // Keep track of open stream IDs to close them properly
    private final Map<String, Integer> streamIds = new ConcurrentHashMap<>();

    @Value("${exchange.binance.use-testnet:false}")
    private boolean useTestnet;

    // Standard Binance Futures WebSocket Base URLs
    private static final String PROD_URL = "wss://fstream.binance.com";
    private static final String TESTNET_URL = "wss://stream.binancefuture.com";

    public BinanceWebSocketAdapter() {
        // Init deferred to @PostConstruct to allow @Value injection
    }

    @PostConstruct
    public void init() {
        String baseUrl = useTestnet ? TESTNET_URL : PROD_URL;
        log.info("Connecting to Binance Futures WebSocket [url={}, testnet={}]", baseUrl, useTestnet);
        if (useTestnet) {
             this.wsClient = new UMWebsocketClientImpl(TESTNET_URL);
        } else {
             this.wsClient = new UMWebsocketClientImpl(PROD_URL);
        }
    }

    @Override
    public Flux<StreamMarketDataEvent> streamTrades(String symbol) {
        return tradeStreams.computeIfAbsent(symbol, s -> {
            Sinks.Many<StreamMarketDataEvent> sink = Sinks.many().replay().limit(1000);
            
            Integer streamId = wsClient.aggTradeStream(s.toLowerCase(), event -> {
                try {
                    AggTradeEvent trade = objectMapper.readValue(event, AggTradeEvent.class);
                    if (trade.price() == null || trade.qty() == null || trade.time() == null) {
                        return; 
                    }
                    
                    StreamMarketDataEvent marketEvent = new StreamMarketDataEvent(
                        "BINANCE_FUTURES",
                        s,
                        EventType.TRADE,
                        trade.price(),
                        trade.qty(),
                        Instant.ofEpochMilli(trade.time()),
                        new RawPayload(event)
                    );
                    
                    sink.tryEmitNext(marketEvent);
                } catch (Exception e) {
                    log.error("Failed to parse trade event: {}", event, e);
                    // Do NOT emit error — that would terminate the shared Sink permanently.
                    // Simply skip this malformed message.
                }
            });
            
            streamIds.put("trade-" + s, streamId);
            return sink;
        }).asFlux();
    }

    @Override
    public Flux<StreamMarketDataEvent> streamBookTicker(String symbol) {
        return tickerStreams.computeIfAbsent(symbol, s -> {
            Sinks.Many<StreamMarketDataEvent> sink = Sinks.many().replay().limit(1000);
            
            Integer streamId = wsClient.bookTicker(s.toLowerCase(), event -> {
                try {
                    BookTickerEvent ticker = objectMapper.readValue(event, BookTickerEvent.class);
                    if (ticker.bidPrice() == null || ticker.askPrice() == null) {
                        return; 
                    }
                    
                    BigDecimal askPrice = ticker.askPrice();
                    BigDecimal bidPrice = ticker.bidPrice();

                    if (askPrice.signum() <= 0 || bidPrice.signum() <= 0) {
                        log.warn("Non-positive price in Binance bookTicker for {}: bid={}, ask={}", s, bidPrice, askPrice);
                        return;
                    }

                    long time = ticker.time() != null ? ticker.time() : System.currentTimeMillis();

                    // price = ask (conservative entry cost for LONG).
                    // Both sides preserved in payload so OrderPlacementService can
                    // choose ask for BUY fills and bid for SELL fills.
                    StreamMarketDataEvent marketEvent = new StreamMarketDataEvent(
                        "BINANCE_FUTURES",
                        s,
                        EventType.BOOK_TICKER,
                        askPrice,
                        BigDecimal.ZERO,
                        Instant.ofEpochMilli(time),
                        new BookTickerPayload(bidPrice, askPrice)
                    );
                    
                    sink.tryEmitNext(marketEvent);
                } catch (Exception e) {
                    log.error("Failed to parse bookTicker event: {}", event, e);
                    // Do NOT emit error — skip malformed message.
                }
            });

            streamIds.put("ticker-" + s, streamId);
            return sink;
        }).asFlux();
    }
    
    @PreDestroy
    public void cleanup() {
        log.info("Closing all Binance streams");
        streamIds.values().forEach(id -> wsClient.closeConnection(id));
        streamIds.clear();
        tradeStreams.clear();
        tickerStreams.clear();
    }
}
