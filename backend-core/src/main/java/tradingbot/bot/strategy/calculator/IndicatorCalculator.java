package tradingbot.bot.strategy.calculator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.strategy.indicator.TechnicalIndicator;

public class IndicatorCalculator {
    private static final Logger LOGGER = Logger.getLogger(IndicatorCalculator.class.getName());
    private static final int CANDLE_LIMIT = 100;

    private final FuturesExchangeService exchangeService;
    private final Map<String, TechnicalIndicator> indicators = new HashMap<>();
    private final RedisTemplate<String, IndicatorValues> redisTemplate;

    public IndicatorCalculator(FuturesExchangeService exchangeService, Map<String, TechnicalIndicator> indicators, RedisTemplate<String, IndicatorValues> redisTemplate) {
        this.exchangeService = exchangeService;
        this.indicators.putAll(indicators);
        this.redisTemplate = redisTemplate;
    }

    // Extensibility: Register new indicators at runtime
    public void registerIndicator(String name, TechnicalIndicator indicator) {
        indicators.put(name, indicator);
    }

    public IndicatorValues computeIndicators(String timeframe, String symbol) {
        if (redisTemplate == null) {
             List<Candle> candles = exchangeService.fetchOhlcv(symbol, timeframe, CANDLE_LIMIT);
             if (!hassufficientData(candles, symbol, timeframe)) {
                return null;
            }
            return calculateIndicatorValues(candles);
        }

        String cacheKey = "indicators:%s:%s".formatted(symbol, timeframe);
        ValueOperations<String, IndicatorValues> valueOps = redisTemplate.opsForValue();
        IndicatorValues cached = valueOps.get(cacheKey);
        List<Candle> candles = exchangeService.fetchOhlcv(symbol, timeframe, CANDLE_LIMIT);
        
        if (shouldInvalidateCache(candles, cached, cacheKey, symbol, timeframe)) {
            cached = null;
        }  
        
        if (cached != null) {
            logCacheHit(symbol, timeframe);
            return cached;
        }
        
        return computeNewIndicators(candles, symbol, timeframe, cacheKey, valueOps);
    }

    private boolean shouldInvalidateCache(List<Candle> candles, IndicatorValues cached, String cacheKey, String symbol, String timeframe) {
        if (candles != null && !candles.isEmpty() && cached != null) {
            long latestCloseTime = candles.get(candles.size() - 1).getCloseTime();
            boolean isNewCandle = latestCloseTime > cached.getCloseTime();
            if (isNewCandle) {
                redisTemplate.delete(cacheKey);
                logCacheInvalidation(candles, cached, symbol, timeframe);
                return true;
            }
        }
        return false;
    }

    private void logCacheInvalidation(List<Candle> candles, IndicatorValues cached, String symbol, String timeframe) {
        if (LOGGER.isLoggable(java.util.logging.Level.INFO)) {
            long latestTime = candles != null && !candles.isEmpty() ? candles.get(candles.size() - 1).getCloseTime() : 0;
            long cachedTime = cached != null ? cached.getCloseTime() : 0;
            LOGGER.info("Cache invalidated for %s on %s timeframe - new candle detected (latest: %d, cached: %d)"
                .formatted(symbol, timeframe, latestTime, cachedTime));
        }
    }

    private void logCacheHit(String symbol, String timeframe) {
        if (LOGGER.isLoggable(java.util.logging.Level.INFO)) {
            LOGGER.info("Cache hit for %s on %s timeframe".formatted(symbol, timeframe));
        }
    }

    private IndicatorValues computeNewIndicators(List<Candle> candles, String symbol, String timeframe, String cacheKey, ValueOperations<String, IndicatorValues> valueOps) {
        if (LOGGER.isLoggable(java.util.logging.Level.INFO)) {
            LOGGER.info("Cache miss. Computing indicators for %s on %s timeframe".formatted(symbol, timeframe));
        }
        
        if (!hassufficientData(candles, symbol, timeframe)) {
            return null;
        }
        
        IndicatorValues values = calculateIndicatorValues(candles);
        valueOps.set(cacheKey, values);
        return values;
    }

    protected boolean hassufficientData(List<Candle> candles, String symbol, String timeframe) {
        if (candles == null || candles.size() < 26) {
            if (LOGGER.isLoggable(java.util.logging.Level.WARNING)) {
                LOGGER.warning("Insufficient data for indicators: %s, timeframe: %s".formatted(symbol, timeframe));
            }
            return false;
        }
        return true;
    }

    protected IndicatorValues calculateIndicatorValues(List<Candle> candles) {
        IndicatorValues values = new IndicatorValues();
        values.setCloseTime(candles.get(candles.size() - 1).getCloseTime());
        
        indicators.forEach((name, indicator) -> {
            double result = indicator.compute(candles, "");
            setIndicatorValue(values, name, result);
        });
        
        return values;
    }

    private void setIndicatorValue(IndicatorValues values, String name, double result) {
        switch (name) {
            case "rsi": values.setRsi(result); break;
            case "macd": values.setMacd(result); break;
            case "signal": values.setSignal(result); break;
            case "lowerBand": values.setLowerBand(result); break;
            case "upperBand": values.setUpperBand(result); break;
            default:
                if (LOGGER.isLoggable(java.util.logging.Level.FINE)) {
                    LOGGER.fine("Unknown indicator name: %s".formatted(name));
                }
                break;
        }
    }
}