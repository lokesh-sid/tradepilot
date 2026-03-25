package tradingbot.bot.strategy.exit;

import tradingbot.bot.strategy.calculator.IndicatorCalculator;
import tradingbot.bot.strategy.calculator.IndicatorValues;

public class RSIExit implements PositionExitCondition {
    private final IndicatorCalculator calculator;
    private double rsiThreshold;

    public RSIExit(IndicatorCalculator calculator, double rsiThreshold) {
        this.calculator = calculator;
        this.rsiThreshold = rsiThreshold;
    }

    @Override
    public boolean shouldExit() {
        IndicatorValues indicators = calculator.computeIndicators("1d", "BTCUSDT");
        return indicators != null && indicators.getRsi() >= rsiThreshold;
    }
}