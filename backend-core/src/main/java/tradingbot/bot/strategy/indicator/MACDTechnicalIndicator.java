package tradingbot.bot.strategy.indicator;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;

import tradingbot.bot.service.BinanceFuturesService.Candle;

public class MACDTechnicalIndicator implements TechnicalIndicator {
    private int fastPeriod;
    private int slowPeriod;
    private int signalPeriod;
    private boolean isSignalLine;

    public MACDTechnicalIndicator(int fastPeriod, int slowPeriod, int signalPeriod, boolean isSignalLine) {
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.signalPeriod = signalPeriod;
        this.isSignalLine = isSignalLine;
    }

    @Override
    public double compute(List<Candle> candles, String timeframe) {
        if (candles.size() < slowPeriod) {
            return Double.NaN;
        }
        BarSeries series = buildBarSeries(candles, timeframe);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrice, fastPeriod, slowPeriod);
        org.ta4j.core.indicators.EMAIndicator signal = new org.ta4j.core.indicators.EMAIndicator(macd, signalPeriod);
        return isSignalLine ? signal.getValue(series.getEndIndex()).doubleValue() : macd.getValue(series.getEndIndex()).doubleValue();
    }

    private BarSeries buildBarSeries(List<Candle> candles, String timeframe) {
        BarSeries series = new BaseBarSeriesBuilder().build();
        for (Candle candle : candles) {
            ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(candle.getCloseTime()), ZoneId.of("UTC"));
            Duration duration = "1d".equals(timeframe) ? Duration.ofDays(1) : Duration.ofDays(7);
            series.addBar(new org.ta4j.core.BaseBar(duration, endTime, DecimalNum.valueOf(candle.getOpen()),
                    DecimalNum.valueOf(candle.getHigh()), DecimalNum.valueOf(candle.getLow()),
                    DecimalNum.valueOf(candle.getClose()), DecimalNum.valueOf(candle.getVolume()), null));
        }
        return series;
    }
}