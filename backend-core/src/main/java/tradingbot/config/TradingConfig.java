package tradingbot.config;

import java.io.Serializable;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class TradingConfig implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_SYMBOL = "BTCUSDT";
    private static final double DEFAULT_TRADE_AMOUNT = 0.001;
    private static final int DEFAULT_LEVERAGE = 3;
    private static final double DEFAULT_TRAILING_STOP_PERCENT = 1.0;
    private static final int DEFAULT_LOOKBACK_PERIOD_RSI = 14;
    private static final double DEFAULT_RSI_OVERSOLD = 30.0;
    private static final double DEFAULT_RSI_OVERBOUGHT = 70.0;
    private static final int DEFAULT_MACD_FAST = 12;
    private static final int DEFAULT_MACD_SLOW = 26;
    private static final int DEFAULT_MACD_SIGNAL = 9;
    private static final int DEFAULT_BB_PERIOD = 20;
    private static final double DEFAULT_BB_STD = 2.0;
    private static final int DEFAULT_INTERVAL = 900;

    @NotBlank(message = "Symbol must not be blank")
    private String symbol;

    @DecimalMin(value = "0.0001", message = "Trade amount must be at least 0.0001")
    @DecimalMax(value = "100.0", message = "Trade amount cannot exceed 100.0")
    private double tradeAmount;

    @Min(value = 1, message = "Leverage must be at least 1")
    @Max(value = 125, message = "Leverage cannot exceed 125")
    private int leverage;

    @DecimalMin(value = "0.1", message = "Trailing stop percent must be at least 0.1")
    @DecimalMax(value = "50.0", message = "Trailing stop percent cannot exceed 50.0")
    private double trailingStopPercent;

    @Min(value = 2, message = "RSI lookback period must be at least 2")
    @Max(value = 500, message = "RSI lookback period cannot exceed 500")
    private int lookbackPeriodRsi;

    @DecimalMin(value = "1.0", message = "RSI oversold threshold must be at least 1.0")
    @DecimalMax(value = "49.0", message = "RSI oversold threshold cannot exceed 49.0")
    private double rsiOversoldThreshold;

    @DecimalMin(value = "51.0", message = "RSI overbought threshold must be at least 51.0")
    @DecimalMax(value = "99.0", message = "RSI overbought threshold cannot exceed 99.0")
    private double rsiOverboughtThreshold;

    @Min(value = 2, message = "MACD fast period must be at least 2")
    @Max(value = 200, message = "MACD fast period cannot exceed 200")
    private int macdFastPeriod;

    @Min(value = 2, message = "MACD slow period must be at least 2")
    @Max(value = 500, message = "MACD slow period cannot exceed 500")
    private int macdSlowPeriod;

    @Min(value = 1, message = "MACD signal period must be at least 1")
    @Max(value = 50, message = "MACD signal period cannot exceed 50")
    private int macdSignalPeriod;

    @Min(value = 2, message = "Bollinger Bands period must be at least 2")
    @Max(value = 200, message = "Bollinger Bands period cannot exceed 200")
    private int bbPeriod;

    @DecimalMin(value = "0.5", message = "Bollinger Bands standard deviation must be at least 0.5")
    @DecimalMax(value = "5.0", message = "Bollinger Bands standard deviation cannot exceed 5.0")
    private double bbStandardDeviation;

    @Min(value = 60, message = "Interval must be at least 60 seconds")
    private int interval;

    private String direction;

    public TradingConfig() {
        this(
            getEnv("TRADING_SYMBOL", DEFAULT_SYMBOL),
            Double.parseDouble(getEnv("TRADE_AMOUNT", String.valueOf(DEFAULT_TRADE_AMOUNT))),
            Integer.parseInt(getEnv("LEVERAGE", String.valueOf(DEFAULT_LEVERAGE))),
            Double.parseDouble(getEnv("TRAILING_STOP_PERCENT", String.valueOf(DEFAULT_TRAILING_STOP_PERCENT))),
            Integer.parseInt(getEnv("LOOKBACK_PERIOD_RSI", String.valueOf(DEFAULT_LOOKBACK_PERIOD_RSI))),
            Double.parseDouble(getEnv("RSI_OVERSOLD", String.valueOf(DEFAULT_RSI_OVERSOLD))),
            Double.parseDouble(getEnv("RSI_OVERBOUGHT", String.valueOf(DEFAULT_RSI_OVERBOUGHT))),
            Integer.parseInt(getEnv("MACD_FAST", String.valueOf(DEFAULT_MACD_FAST))),
            Integer.parseInt(getEnv("MACD_SLOW", String.valueOf(DEFAULT_MACD_SLOW))),
            Integer.parseInt(getEnv("MACD_SIGNAL", String.valueOf(DEFAULT_MACD_SIGNAL))),
            Integer.parseInt(getEnv("BB_PERIOD", String.valueOf(DEFAULT_BB_PERIOD))),
            Double.parseDouble(getEnv("BB_STD", String.valueOf(DEFAULT_BB_STD))),
            Integer.parseInt(getEnv("INTERVAL", String.valueOf(DEFAULT_INTERVAL)))
        );
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    public TradingConfig(String symbol, double tradeAmount, int leverage, double trailingStopPercent,
                        int lookbackPeriodRsi, double rsiOversoldThreshold, double rsiOverboughtThreshold,
                        int macdFastPeriod, int macdSlowPeriod, int macdSignalPeriod, int bbPeriod,
                        double bbStandardDeviation, int interval) {
        this.symbol = symbol;
        this.tradeAmount = tradeAmount;
        this.leverage = leverage;
        this.trailingStopPercent = trailingStopPercent;
        this.lookbackPeriodRsi = lookbackPeriodRsi;
        this.rsiOversoldThreshold = rsiOversoldThreshold;
        this.rsiOverboughtThreshold = rsiOverboughtThreshold;
        this.macdFastPeriod = macdFastPeriod;
        this.macdSlowPeriod = macdSlowPeriod;
        this.macdSignalPeriod = macdSignalPeriod;
        this.bbPeriod = bbPeriod;
        this.bbStandardDeviation = bbStandardDeviation;
        this.interval = interval;
        this.direction = null;
    }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public double getTradeAmount() { return tradeAmount; }
    public void setTradeAmount(double tradeAmount) { this.tradeAmount = tradeAmount; }
    
    public int getLeverage() { return leverage; }
    public void setLeverage(int leverage) { this.leverage = leverage; }
    
    public double getTrailingStopPercent() { return trailingStopPercent; }
    public void setTrailingStopPercent(double trailingStopPercent) { this.trailingStopPercent = trailingStopPercent; }
    
    public int getLookbackPeriodRsi() { return lookbackPeriodRsi; }
    public void setLookbackPeriodRsi(int lookbackPeriodRsi) { this.lookbackPeriodRsi = lookbackPeriodRsi; }
    
    public double getRsiOversoldThreshold() { return rsiOversoldThreshold; }
    public void setRsiOversoldThreshold(double rsiOversoldThreshold) { this.rsiOversoldThreshold = rsiOversoldThreshold; }
    
    public double getRsiOverboughtThreshold() { return rsiOverboughtThreshold; }
    public void setRsiOverboughtThreshold(double rsiOverboughtThreshold) { this.rsiOverboughtThreshold = rsiOverboughtThreshold; }
    
    public int getMacdFastPeriod() { return macdFastPeriod; }
    public void setMacdFastPeriod(int macdFastPeriod) { this.macdFastPeriod = macdFastPeriod; }
    
    public int getMacdSlowPeriod() { return macdSlowPeriod; }
    public void setMacdSlowPeriod(int macdSlowPeriod) { this.macdSlowPeriod = macdSlowPeriod; }
    
    public int getMacdSignalPeriod() { return macdSignalPeriod; }
    public void setMacdSignalPeriod(int macdSignalPeriod) { this.macdSignalPeriod = macdSignalPeriod; }
    
    public int getBbPeriod() { return bbPeriod; }
    public void setBbPeriod(int bbPeriod) { this.bbPeriod = bbPeriod; }
    
    public double getBbStandardDeviation() { return bbStandardDeviation; }
    public void setBbStandardDeviation(double bbStandardDeviation) { this.bbStandardDeviation = bbStandardDeviation; }
    
    public int getInterval() { return interval; }
    public void setInterval(int interval) { this.interval = interval; }
}