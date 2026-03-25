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
 * Rate-limited wrapper for BinanceFuturesService using Resilience4j
 * 
 * This service implements different rate limiting strategies for different types of operations:
 * - Trading operations (orders, leverage): 8 requests per 10 seconds
 * - Market data operations: 30 requests per second  
 * - Account data operations: 2 requests per second
 * 
 * Also includes circuit breaker and retry mechanisms for enhanced reliability.
 */
public class RateLimitedBinanceFuturesService implements FuturesExchangeService {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitedBinanceFuturesService.class);
    
    private final BinanceFuturesService binanceService;
    
    public RateLimitedBinanceFuturesService(String apiKey, String apiSecret, EventPublisher eventPublisher) {
        this.binanceService = new BinanceFuturesService(apiKey, apiSecret, eventPublisher);
        logger.info("Rate-limited Binance Futures service initialized");
    }
    
    public RateLimitedBinanceFuturesService(String apiKey, String apiSecret) {
        this(apiKey, apiSecret, null);
    }
    
    /**
     * Fetch OHLCV data with market data rate limiting
     */
    @Override
    @RateLimiter(name = "binance-market")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackFetchOhlcv")
    @Retry(name = "binance-api")
    public List<BinanceFuturesService.Candle> fetchOhlcv(String symbol, String timeframe, int limit) {
        logger.debug("Fetching OHLCV data for {} with timeframe {} and limit {}", symbol, timeframe, limit);
        return binanceService.fetchOhlcv(symbol, timeframe, limit);
    }

    /**
     * Get current price with market data rate limiting
     */
    @Override
    @RateLimiter(name = "binance-market")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackGetCurrentPrice")
    @Retry(name = "binance-api")
    public double getCurrentPrice(String symbol) {
        logger.debug("Fetching current price for {}", symbol);
        return binanceService.getCurrentPrice(symbol);
    }

    /**
     * Get margin balance with account data rate limiting
     */
    @Override
    @RateLimiter(name = "binance-account")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackGetMarginBalance")
    @Retry(name = "binance-api")
    public double getMarginBalance() {
        logger.debug("Fetching margin balance");
        return binanceService.getMarginBalance();
    }
    
    /**
     * Get 24-hour stats with market data rate limiting
     */
    @Override
    @RateLimiter(name = "binance-market")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackGet24HourStats")
    @Retry(name = "binance-api")
    public Ticker24hrStats get24HourStats(String symbol) {
        logger.debug("Fetching 24h stats for {}", symbol);
        return binanceService.get24HourStats(symbol);
    }

    /**
     * Set leverage with trading operations rate limiting
     */
    @Override
    @RateLimiter(name = "binance-trading")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackSetLeverage")
    @Retry(name = "binance-api")
    public void setLeverage(String symbol, int leverage) {
        logger.info("Setting leverage to {}x for {}", leverage, symbol);
        binanceService.setLeverage(symbol, leverage);
    }

    /**
     * Enter long position with trading operations rate limiting
     */
    @Override
    @RateLimiter(name = "binance-trading")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackEnterLongPosition")
    @Retry(name = "binance-api")
    public OrderResult enterLongPosition(String symbol, double tradeAmount) {
        logger.info("Entering long position: {} {} at market price", tradeAmount, symbol);
        return binanceService.enterLongPosition(symbol, tradeAmount);
    }

    /**
     * Enter short position with trading operations rate limiting
     */
    @Override
    @RateLimiter(name = "binance-trading")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackEnterShortPosition")
    @Retry(name = "binance-api")
    public OrderResult enterShortPosition(String symbol, double tradeAmount) {
        logger.info("Entering short position: {} {} at market price", tradeAmount, symbol);
        return binanceService.enterShortPosition(symbol, tradeAmount);
    }

    /**
     * Exit long position with trading operations rate limiting
     */
    @Override
    @RateLimiter(name = "binance-trading")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackExitLongPosition")
    @Retry(name = "binance-api")
    public OrderResult exitLongPosition(String symbol, double tradeAmount) {
        logger.info("Exiting long position: {} {} at market price", tradeAmount, symbol);
        return binanceService.exitLongPosition(symbol, tradeAmount);
    }

    /**
     * Exit short position with trading operations rate limiting
     */
    @Override
    @RateLimiter(name = "binance-trading")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackExitShortPosition")
    @Retry(name = "binance-api")
    public OrderResult exitShortPosition(String symbol, double tradeAmount) {
        logger.info("Exiting short position: {} {} at market price", tradeAmount, symbol);
        return binanceService.exitShortPosition(symbol, tradeAmount);
    }
    
    /**
     * Place stop-loss order with trading operations rate limiting
     */
    @Override
    @RateLimiter(name = "binance-trading")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackPlaceStopLossOrder")
    @Retry(name = "binance-api")
    public OrderResult placeStopLossOrder(String symbol, String side, double quantity, double stopPrice) {
        logger.info("Placing stop-loss order: {} {} {} @ {}", side, quantity, symbol, stopPrice);
        return binanceService.placeStopLossOrder(symbol, side, quantity, stopPrice);
    }
    
    /**
     * Place take-profit order with trading operations rate limiting
     */
    @Override
    @RateLimiter(name = "binance-trading")
    @CircuitBreaker(name = "binance-api", fallbackMethod = "fallbackPlaceTakeProfitOrder")
    @Retry(name = "binance-api")
    public OrderResult placeTakeProfitOrder(String symbol, String side, double quantity, double takeProfitPrice) {
        logger.info("Placing take-profit order: {} {} {} @ {}", side, quantity, symbol, takeProfitPrice);
        return binanceService.placeTakeProfitOrder(symbol, side, quantity, takeProfitPrice);
    }

    // Fallback methods for circuit breaker

    /**
     * Fallback method for fetchOhlcv when circuit breaker is open
     */
    public List<BinanceFuturesService.Candle> fallbackFetchOhlcv(String symbol, String timeframe, int limit, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to fetch OHLCV data for {} - {}", symbol, ex.getMessage());
        throw new BotOperationException("fetch_ohlcv", ex.getMessage(), ex);
    }

    /**
     * Fallback method for getCurrentPrice when circuit breaker is open
     */
    public double fallbackGetCurrentPrice(String symbol, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to fetch current price for {} - {}", symbol, ex.getMessage());
        throw new BotOperationException("fetch_price", ex.getMessage(), ex);
    }

    /**
     * Fallback method for getMarginBalance when circuit breaker is open
     */
    public double fallbackGetMarginBalance(Exception ex) {
        logger.error("Circuit breaker fallback: Failed to fetch margin balance - {}", ex.getMessage());
        throw new BotOperationException("fetch_balance", ex.getMessage(), ex);
    }
    
    /**
     * Fallback method for get24HourStats when circuit breaker is open
     */
    public Ticker24hrStats fallbackGet24HourStats(String symbol, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to fetch 24h stats for {} - {}", symbol, ex.getMessage());
        throw new BotOperationException("fetch_24h_stats", ex.getMessage(), ex);
    }

    /**
     * Fallback method for setLeverage when circuit breaker is open
     */
    public void fallbackSetLeverage(String symbol, int leverage, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to set leverage for {} - {}", symbol, ex.getMessage());
        throw new BotOperationException("set_leverage", ex.getMessage(), ex);
    }

    /**
     * Fallback method for enterLongPosition when circuit breaker is open
     */
    public OrderResult fallbackEnterLongPosition(String symbol, double tradeAmount, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to enter long position for {} - {}", symbol, ex.getMessage());
        throw new BotOperationException("enter_long_position", ex.getMessage(), ex);
    }

    /**
     * Fallback method for enterShortPosition when circuit breaker is open
     */
    public OrderResult fallbackEnterShortPosition(String symbol, double tradeAmount, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to enter short position for {} - {}", symbol, ex.getMessage());
        throw new BotOperationException("enter_short_position", ex.getMessage(), ex);
    }

    /**
     * Fallback method for exitLongPosition when circuit breaker is open
     */
    public OrderResult fallbackExitLongPosition(String symbol, double tradeAmount, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to exit long position for {} - {}", symbol, ex.getMessage());
        throw new BotOperationException("exit_long_position", ex.getMessage(), ex);
    }

    /**
     * Fallback method for exitShortPosition when circuit breaker is open
     */
    public OrderResult fallbackExitShortPosition(String symbol, double tradeAmount, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to exit short position for {} - {}", symbol, ex.getMessage());
        throw new BotOperationException("exit_short_position", ex.getMessage(), ex);
    }
    
    /**
     * Fallback method for placeStopLossOrder when circuit breaker is open
     */
    public OrderResult fallbackPlaceStopLossOrder(String symbol, String side, double quantity, double stopPrice, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to place stop-loss order for {} - {}", symbol, ex.getMessage());
        throw new BotOperationException("place_stop_loss", ex.getMessage(), ex);
    }
    
    /**
     * Fallback method for placeTakeProfitOrder when circuit breaker is open
     */
    public OrderResult fallbackPlaceTakeProfitOrder(String symbol, String side, double quantity, double takeProfitPrice, Exception ex) {
        logger.error("Circuit breaker fallback: Failed to place take-profit order for {} - {}", symbol, ex.getMessage());
        throw new BotOperationException("place_take_profit", ex.getMessage(), ex);
    }
}
