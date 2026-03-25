package tradingbot.infrastructure.marketdata;

import java.time.Duration;

import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;
import tradingbot.domain.market.StreamMarketDataEvent;

/**
 * Common interface for all exchange WebSocket implementations.
 * Returns hot reactive streams of market data.
 */
public interface ExchangeWebSocketClient {

    /**
     * Identifies this adapter's exchange (e.g. "BINANCE_FUTURES", "BYBIT_LINEAR").
     * Used by the composite service for priority-based routing and logging.
     * Returns "UNKNOWN" by default so existing anonymous/test implementations
     * remain source-compatible.
     */
    default String getExchangeName() {
        return "UNKNOWN";
    }
    
    /**
     * Stream real-time trades.
     */
    Flux<StreamMarketDataEvent> streamTrades(String symbol);

    /**
     * Stream best bid/ask updates.
     */
    Flux<StreamMarketDataEvent> streamBookTicker(String symbol);

    /**
     * Standard resilience pattern for WebSocket streams.
     * Retries indefinitely with exponential backoff (1s to 60s).
     */
    default Flux<StreamMarketDataEvent> resilient(Flux<StreamMarketDataEvent> source) {
        return source.retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
            .maxBackoff(Duration.ofSeconds(60))
            .doBeforeRetry(signal -> 
                System.out.println("Retrying WebSocket connection: " + signal.totalRetries())
            ));
    }
}
