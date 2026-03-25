package tradingbot.util;

import java.util.List;

import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.FuturesTradingBot.BotParams;
import tradingbot.bot.TradeDirection;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.bot.strategy.calculator.IndicatorCalculator;
import tradingbot.bot.strategy.exit.PositionExitCondition;
import tradingbot.bot.strategy.tracker.TrailingStopTracker;
import tradingbot.config.TradingConfig;

public class FuturesTradingBotTestUtils {
    public static final String SYMBOL = "BTCUSDT";
    public static final double TRADE_AMOUNT = 0.001;
    public static final int LEVERAGE = 3;
    public static final double TRAILING_STOP_PERCENT = 1.0;
    public static final int RSI_PERIOD = 14;
    public static final double RSI_OVERSOLD = 30.0;
    public static final double RSI_OVERBOUGHT = 70.0;
    public static final int MACD_FAST = 12;
    public static final int MACD_SLOW = 26;
    public static final int MACD_SIGNAL = 9;
    public static final int BB_PERIOD = 20;
    public static final double BB_STD = 2.0;
    public static final int INTERVAL = 900;

    public static void invokePrivateMethod(FuturesTradingBot bot, String methodName) {
        try {
            var method = FuturesTradingBot.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(bot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke private method: " + methodName, e);
        }
    }

    public static Object getFieldValue(FuturesTradingBot bot, String fieldName) {
        try {
            var field = FuturesTradingBot.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(bot);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field value: " + fieldName, e);
        }
    }

    public static BotParams getBotParams(
            FuturesExchangeService exchangeService,
            IndicatorCalculator indicatorCalculator,
            TrailingStopTracker trailingStopTracker,
            SentimentAnalyzer sentimentAnalyzer,
            List<PositionExitCondition> exitConditions,
            TradeDirection direction) {
        
        TradingConfig config = new TradingConfig(SYMBOL, TRADE_AMOUNT, LEVERAGE, TRAILING_STOP_PERCENT, 
            RSI_PERIOD, RSI_OVERSOLD, RSI_OVERBOUGHT, MACD_FAST, MACD_SLOW, MACD_SIGNAL, 
            BB_PERIOD, BB_STD, INTERVAL);

        return new BotParams.Builder()
            .exchangeService(exchangeService)
            .indicatorCalculator(indicatorCalculator)
            .trailingStopTracker(trailingStopTracker)
            .sentimentAnalyzer(sentimentAnalyzer)
            .exitConditions(exitConditions)
            .config(config)
            .tradeDirection(direction)
            .skipLeverageInit(true)
            .build();
    }
}
