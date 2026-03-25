package tradingbot.bot.strategy.indicator;

import java.util.List;

import tradingbot.bot.service.BinanceFuturesService.Candle;

public interface TechnicalIndicator {
    double compute(List<Candle> candles, String timeframe);
}