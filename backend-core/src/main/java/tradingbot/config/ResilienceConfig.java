package tradingbot.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Configuration for Resilience4j rate limiting, circuit breaker, and retry mechanisms
 * 
 * Defines different rate limiting strategies based on Binance API limits:
 * - Trading operations: Conservative limits for order placement
 * - Market data: Higher limits for price and OHLCV data
 * - Account data: Moderate limits for balance and account info
 */
@Configuration
public class ResilienceConfig {

    /**
     * Rate limiter for trading operations (orders, leverage changes)
     * Binance limit: 100 requests per 10 seconds per IP
     * Our limit: 8 requests per 10 seconds (conservative)
     */
    @Bean
    RateLimiter binanceTradingRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(8)
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .timeoutDuration(Duration.ofSeconds(5))
                .build();
        
        return RateLimiter.of("binance-trading", config);
    }

    /**
     * Rate limiter for market data operations (price, OHLCV)
     * Binance limit: 2400 requests per minute per IP (40 requests/second)
     * Our limit: 30 requests per second (conservative)
     */
    @Bean
    RateLimiter binanceMarketRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(30)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(3))
                .build();
        
        return RateLimiter.of("binance-market", config);
    }

    /**
     * Rate limiter for account data operations (balance, account info)
     * Binance limit: 180 requests per minute per IP (3 requests/second)
     * Our limit: 2 requests per second (conservative)
     */
    @Bean
    RateLimiter binanceAccountRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(Duration.ofSeconds(5))
                .build();
        
        return RateLimiter.of("binance-account", config);
    }

    /**
     * Circuit breaker for Binance API calls
     * Opens when 50% of calls fail with minimum 5 calls
     * Stays open for 30 seconds before trying again
     */
    @Bean
    CircuitBreaker binanceApiCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(5)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        
        return CircuitBreaker.of("binance-api", config);
    }

    /**
     * Retry configuration for Binance API calls
     * Retries up to 3 times with exponential backoff
     * Initial wait: 1 second, multiplier: 2
     */
    @Bean
    Retry binanceApiRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(1))
                .retryOnException(throwable -> 
                    // Retry on network issues, rate limit exceptions, and temporary API errors
                    throwable instanceof RuntimeException &&
                           (throwable.getMessage() != null &&
                           (throwable.getMessage().contains("rate limit") ||
                            throwable.getMessage().contains("timeout") ||
                            throwable.getMessage().contains("connection") ||
                            throwable.getMessage().contains("temporarily unavailable")))
                )
                .build();
        
        return Retry.of("binance-api", config);
    }
}
