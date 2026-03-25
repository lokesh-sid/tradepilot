package tradingbot.bot.strategy.indicator;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;

import tradingbot.bot.service.BinanceFuturesService.Candle;

public class RSITechnicalIndicator implements TechnicalIndicator {
    private int period;

    public RSITechnicalIndicator(int period) {
        this.period = period;
    }

    @Override
    public double compute(List<Candle> candles, String timeframe) {
        if (candles.size() < period) {
            return Double.NaN;
        }
        BarSeries series = buildBarSeries(candles, timeframe);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, period);
        return rsi.getValue(series.getEndIndex()).doubleValue();
    }

    private BarSeries buildBarSeries(List<Candle> candles, String timeframe) {
        BarSeries series = new BaseBarSeriesBuilder().build();
        for (Candle candle : candles) {
            ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(candle.getCloseTime()),
                    ZoneId.of("UTC"));
            Duration duration = "1d".equals(timeframe) ? Duration.ofDays(1) : Duration.ofDays(7);
            series.addBar(new org.ta4j.core.BaseBar(duration, endTime, DecimalNum.valueOf(candle.getOpen()),
                    DecimalNum.valueOf(candle.getHigh()), DecimalNum.valueOf(candle.getLow()),
                    DecimalNum.valueOf(candle.getClose()), DecimalNum.valueOf(candle.getVolume()), null, period));
        }
        return series;
    }
}