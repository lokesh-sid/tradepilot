package tradingbot.bot.strategy.indicator;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNum;

import tradingbot.bot.service.BinanceFuturesService.Candle;

public class BollingerBandsIndicator implements TechnicalIndicator {
    private int period;
    private double standardDeviation;
    private boolean isLowerBand;

    public BollingerBandsIndicator(int period, double standardDeviation, boolean isLowerBand) {
        this.period = period;
        this.standardDeviation = standardDeviation;
        this.isLowerBand = isLowerBand;
    }

    @Override
    public double compute(List<Candle> candles, String timeframe) {
        if (candles.size() < period) {
            return Double.NaN;
        }
        BarSeries series = buildBarSeries(candles, timeframe);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        BollingerBandsMiddleIndicator middle = new BollingerBandsMiddleIndicator(closePrice);
        StandardDeviationIndicator std = new StandardDeviationIndicator(closePrice, period);
        if (isLowerBand) {
            BollingerBandsLowerIndicator lower = new BollingerBandsLowerIndicator(middle, std, DecimalNum.valueOf(standardDeviation));
            return lower.getValue(series.getEndIndex()).doubleValue();
        } else {
            BollingerBandsUpperIndicator upper = new BollingerBandsUpperIndicator(middle, std, DecimalNum.valueOf(standardDeviation));
            return upper.getValue(series.getEndIndex()).doubleValue();
        }
    }

    private BarSeries buildBarSeries(List<Candle> candles, String timeframe) {
        BarSeries series = new BaseBarSeriesBuilder().build();
        for (Candle candle : candles) {
            ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(candle.getCloseTime()), ZoneId.of("UTC"));
            Duration duration = "1d".equals(timeframe) ? Duration.ofDays(1) : Duration.ofDays(7);
            series.addBar(new BaseBar(duration, endTime, DecimalNum.valueOf(candle.getOpen()),
                    DecimalNum.valueOf(candle.getHigh()), DecimalNum.valueOf(candle.getLow()),
                    DecimalNum.valueOf(candle.getClose()), DecimalNum.valueOf(candle.getVolume()), null));
        }
        return series;
    }
}