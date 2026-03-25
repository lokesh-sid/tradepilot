package tradingbot.bot.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import tradingbot.bot.controller.exception.BotOperationException;
import tradingbot.bot.messaging.EventPublisher;

/**
 * Rate-limited wrapper for BybitFuturesService using Resilience4j
 * 
 * Applies:
 * - Rate limiting to comply with Bybit API limits
 * - Circuit breaker to prevent cascading failures
 * - Retry logic for transient errors
 * 
 * Bybit Rate Limits (V5 Unified Account):
 * - Trading: 10 requests per second
 * - Market Data: 50 requests per second
 * - Account: 5 requests per second
 */
public class RateLimitedBybitFuturesService implements FuturesExchangeService {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitedBybitFuturesService.class);
    
    private final BybitFuturesService delegate;
    
    public RateLimitedBybitFuturesService(String apiKey, String apiSecret, EventPublisher eventPublisher) {
        this.delegate = new BybitFuturesService(apiKey, apiSecret, eventPublisher);
        logger.info("Rate-limited Bybit Futures Service initialized");
    }
    
    public RateLimitedBybitFuturesService(String apiKey, String apiSecret, String baseDomain, EventPublisher eventPublisher) {
        this.delegate = new BybitFuturesService(apiKey, apiSecret, baseDomain, eventPublisher);
        logger.info("Rate-limited Bybit Futures Service initialized (domain: {})", baseDomain);
    }
    
    @Override
    @RateLimiter(name = "bybit-market", fallbackMethod = "fetchOhlcvFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-market")
    public List<BinanceFuturesService.Candle> fetchOhlcv(String symbol, String timeframe, int limit) {
        logger.debug("Rate-limited OHLCV fetch for {}", symbol);
        return delegate.fetchOhlcv(symbol, timeframe, limit);
    }
    
    @Override
    @RateLimiter(name = "bybit-market", fallbackMethod = "getCurrentPriceFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-market")
    public double getCurrentPrice(String symbol) {
        logger.debug("Rate-limited price fetch for {}", symbol);
        return delegate.getCurrentPrice(symbol);
    }
    
    @Override
    @RateLimiter(name = "bybit-account", fallbackMethod = "getMarginBalanceFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-account")
    public double getMarginBalance() {
        logger.debug("Rate-limited balance fetch");
        return delegate.getMarginBalance();
    }
    
    @Override
    @RateLimiter(name = "bybit-market", fallbackMethod = "get24HourStatsFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-market")
    public Ticker24hrStats get24HourStats(String symbol) {
        logger.debug("Rate-limited 24h stats fetch for {}", symbol);
        return delegate.get24HourStats(symbol);
    }
    
    @Override
    @RateLimiter(name = "bybit-account", fallbackMethod = "setLeverageFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-account")
    public void setLeverage(String symbol, int leverage) {
        logger.info("Rate-limited leverage setting: {}x for {}", leverage, symbol);
        delegate.setLeverage(symbol, leverage);
    }
    
    @Override
    @RateLimiter(name = "bybit-trading", fallbackMethod = "enterLongPositionFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-trading")
    public OrderResult enterLongPosition(String symbol, double tradeAmount) {
        logger.info("Rate-limited LONG entry: {} units of {}", tradeAmount, symbol);
        return delegate.enterLongPosition(symbol, tradeAmount);
    }
    
    @Override
    @RateLimiter(name = "bybit-trading", fallbackMethod = "enterShortPositionFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-trading")
    public OrderResult enterShortPosition(String symbol, double tradeAmount) {
        logger.info("Rate-limited SHORT entry: {} units of {}", tradeAmount, symbol);
        return delegate.enterShortPosition(symbol, tradeAmount);
    }
    
    @Override
    @RateLimiter(name = "bybit-trading", fallbackMethod = "exitLongPositionFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-trading")
    public OrderResult exitLongPosition(String symbol, double tradeAmount) {
        logger.info("Rate-limited LONG exit: {} units of {}", tradeAmount, symbol);
        return delegate.exitLongPosition(symbol, tradeAmount);
    }
    
    @Override
    @RateLimiter(name = "bybit-trading", fallbackMethod = "exitShortPositionFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-trading")
    public OrderResult exitShortPosition(String symbol, double tradeAmount) {
        logger.info("Rate-limited SHORT exit: {} units of {}", tradeAmount, symbol);
        return delegate.exitShortPosition(symbol, tradeAmount);
    }
    
    @Override
    @RateLimiter(name = "bybit-trading", fallbackMethod = "placeStopLossOrderFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-trading")
    public OrderResult placeStopLossOrder(String symbol, String side, double quantity, double stopPrice) {
        logger.info("Rate-limited stop-loss order: {} {} {} @ {}", side, quantity, symbol, stopPrice);
        return delegate.placeStopLossOrder(symbol, side, quantity, stopPrice);
    }
    
    @Override
    @RateLimiter(name = "bybit-trading", fallbackMethod = "placeTakeProfitOrderFallback")
    @CircuitBreaker(name = "bybit-api")
    @Retry(name = "bybit-trading")
    public OrderResult placeTakeProfitOrder(String symbol, String side, double quantity, double takeProfitPrice) {
        logger.info("Rate-limited take-profit order: {} {} {} @ {}", side, quantity, symbol, takeProfitPrice);
        return delegate.placeTakeProfitOrder(symbol, side, quantity, takeProfitPrice);
    }
    
    // ==================== Fallback Methods ====================
    
    @SuppressWarnings("unused")
    private List<BinanceFuturesService.Candle> fetchOhlcvFallback(String symbol, String timeframe, int limit, Throwable t) {
        logger.error("Fallback triggered for fetchOhlcv: {}", t.getMessage());
        throw new BotOperationException("fetch_ohlcv", t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private double getCurrentPriceFallback(String symbol, Throwable t) {
        logger.error("Fallback triggered for getCurrentPrice: {}", t.getMessage());
        throw new BotOperationException("fetch_price", t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private double getMarginBalanceFallback(Throwable t) {
        logger.error("Fallback triggered for getMarginBalance: {}", t.getMessage());
        throw new BotOperationException("fetch_balance", t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private Ticker24hrStats get24HourStatsFallback(String symbol, Throwable t) {
        logger.error("Fallback triggered for get24HourStats: {}", t.getMessage());
        throw new BotOperationException("fetch_24h_stats", t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private void setLeverageFallback(String symbol, int leverage, Throwable t) {
        logger.error("Fallback triggered for setLeverage: {}", t.getMessage());
        throw new BotOperationException("set_leverage", t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private OrderResult enterLongPositionFallback(String symbol, double tradeAmount, Throwable t) {
        logger.error("Fallback triggered for enterLongPosition: {}", t.getMessage());
        throw new BotOperationException("enter_long_position", t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private OrderResult enterShortPositionFallback(String symbol, double tradeAmount, Throwable t) {
        logger.error("Fallback triggered for enterShortPosition: {}", t.getMessage());
        throw new BotOperationException("enter_short_position", t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private OrderResult exitLongPositionFallback(String symbol, double tradeAmount, Throwable t) {
        logger.error("Fallback triggered for exitLongPosition: {}", t.getMessage());
        throw new BotOperationException("exit_long_position", t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private OrderResult exitShortPositionFallback(String symbol, double tradeAmount, Throwable t) {
        logger.error("Fallback triggered for exitShortPosition: {}", t.getMessage());
        throw new BotOperationException("exit_short_position", t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private OrderResult placeStopLossOrderFallback(String symbol, String side, double quantity, double stopPrice, Throwable t) {
        logger.error("Fallback triggered for placeStopLossOrder: {}", t.getMessage());
        throw new BotOperationException("place_stop_loss", t.getMessage(), t);
    }
    
    @SuppressWarnings("unused")
    private OrderResult placeTakeProfitOrderFallback(String symbol, String side, double quantity, double takeProfitPrice, Throwable t) {
        logger.error("Fallback triggered for placeTakeProfitOrder: {}", t.getMessage());
        throw new BotOperationException("place_take_profit", t.getMessage(), t);
    }
}
