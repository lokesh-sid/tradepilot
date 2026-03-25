package tradingbot.bot.model;

import tradingbot.bot.strategy.calculator.IndicatorValues;

/**
 * Encapsulates market indicator data for both daily and weekly timeframes.
 * <p>
 * Used by the trading bot to make entry and exit decisions based on technical analysis.
 *
 * @param dailyIndicators  Technical indicator values computed for the daily timeframe
 * @param weeklyIndicators Technical indicator values computed for the weekly timeframe
 * @see IndicatorValues
 */
public record MarketData(
    IndicatorValues dailyIndicators,
    IndicatorValues weeklyIndicators,
    double currentPrice,
    double priceChange24h,
    double volume,
    String trend,
    String sentiment,
    double sentimentScore
) {

    public MarketData(IndicatorValues dailyIndicators, IndicatorValues weeklyIndicators) {
        this(dailyIndicators, weeklyIndicators, 0.0, 0.0, 0.0, "UNKNOWN", "UNKNOWN", 0.0);
    }

    public MarketData(double currentPrice, double priceChange24h, double volume, String trend, String sentiment, double sentimentScore) {
        this(null, null, currentPrice, priceChange24h, volume, trend, sentiment, sentimentScore);
    }
}