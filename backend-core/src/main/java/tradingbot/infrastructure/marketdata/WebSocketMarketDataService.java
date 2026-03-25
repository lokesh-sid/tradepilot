package tradingbot.infrastructure.marketdata;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import tradingbot.bot.messaging.EventTopic;
import tradingbot.domain.market.StreamMarketDataEvent;

/**
 * Composite WebSocket service that dynamically routes to exchange adapters
 * and publishes throttled events to Kafka.
 *
 * <p>Adapters are injected as a priority-ordered list.  The first adapter in
 * the list is the primary; subsequent adapters form the fallback chain via
 * {@code onErrorResume}.  Adding a new exchange adapter (e.g. dYdX, Kraken)
 * only requires registering a new {@link ExchangeWebSocketClient} bean —
 * no changes to this class are needed.
 */
@Service
@Primary
public class WebSocketMarketDataService implements ExchangeWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(WebSocketMarketDataService.class);
    private final List<ExchangeWebSocketClient> adapters;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MarketDataSanitizer sanitizer;

    @Value("${market.data.kafka-publish-interval-ms:500}")
    private long kafkaPublishIntervalMs;

    /** Per exchange:symbol timestamp gate to throttle Kafka publishes without affecting the Flux pipeline. */
    private final Map<String, Long> lastPublishTime = new ConcurrentHashMap<>();

    /**
     * Spring injects all {@link ExchangeWebSocketClient} beans except this
     * composite service itself (excluded because this class is {@code @Primary}
     * and the list parameter is not self-referencing — Spring resolves the
     * non-primary beans for collection injection).
     */
    public WebSocketMarketDataService(
            List<ExchangeWebSocketClient> adapters,
            KafkaTemplate<String, Object> kafkaTemplate,
            MarketDataSanitizer sanitizer) {
        // Filter out this composite service in case Spring includes it
        this.adapters = adapters.stream()
                .filter(a -> !(a instanceof WebSocketMarketDataService))
                .collect(Collectors.toList());
        this.kafkaTemplate = kafkaTemplate;
        this.sanitizer = sanitizer;

        log.info("WebSocketMarketDataService initialized with {} exchange adapter(s): {}",
                this.adapters.size(),
                this.adapters.stream()
                        .map(ExchangeWebSocketClient::getExchangeName)
                        .collect(Collectors.joining(" → ")));
    }

    @Override
    public String getExchangeName() {
        return "COMPOSITE";
    }

    @Override
    public Flux<StreamMarketDataEvent> streamTrades(String symbol) {
        return resilient(buildFallbackChain(symbol, ExchangeWebSocketClient::streamTrades))
                .filter(sanitizer::isValid)
                .doOnNext(this::publishToKafka);
    }

    @Override
    public Flux<StreamMarketDataEvent> streamBookTicker(String symbol) {
        return resilient(buildFallbackChain(symbol, ExchangeWebSocketClient::streamBookTicker))
                .filter(sanitizer::isValid)
                .map(sanitizer::sanitize)
                .doOnNext(this::publishToKafka);
    }

    /**
     * Builds a priority-based fallback chain from the injected adapter list.
     * The first adapter is the primary source; each subsequent adapter is
     * wired as an {@code onErrorResume} fallback.
     */
    private Flux<StreamMarketDataEvent> buildFallbackChain(
            String symbol,
            BiFunction<ExchangeWebSocketClient, String, Flux<StreamMarketDataEvent>> streamSelector) {

        if (adapters.isEmpty()) {
            return Flux.error(new IllegalStateException("No exchange WebSocket adapters available"));
        }

        Flux<StreamMarketDataEvent> chain = streamSelector.apply(adapters.get(0), symbol);

        for (int i = 1; i < adapters.size(); i++) {
            final ExchangeWebSocketClient fallback = adapters.get(i);
            final ExchangeWebSocketClient failed = adapters.get(i - 1);
            chain = chain.onErrorResume(e -> {
                log.warn("{} stream failed for {}, failing over to {}",
                        failed.getExchangeName(), symbol, fallback.getExchangeName());
                return streamSelector.apply(fallback, symbol);
            });
        }

        return chain;
    }
    
    private void publishToKafka(StreamMarketDataEvent event) {
        long now = System.currentTimeMillis();
        String key = event.exchange() + ":" + event.symbol();
        
        // Atomically check and update the throttle timestamp
        Long previousTime = lastPublishTime.compute(key, (k, lastTime) -> {
            if (lastTime != null && now - lastTime < kafkaPublishIntervalMs) {
                return lastTime; // Not enough time has passed, keep old time
            }
            return now; // Time to publish, update to new time
        });

        // If the map returned the old time (and it wasn't exactly 'now' down to the ms), we skip publishing
        if (previousTime != now) {
            return;
        }

        try {
            kafkaTemplate.send(EventTopic.MARKET_DATA.getTopicName(), event.symbol(), event);
            kafkaTemplate.flush(); // Ensure all messages are sent before shutdown
        } catch (Exception e) {
            log.error("Failed to publish market data to Kafka for {}", event.symbol(), e);
        }
    }
}
