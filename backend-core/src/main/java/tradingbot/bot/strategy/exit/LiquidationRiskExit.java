package tradingbot.bot.strategy.exit;

import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.strategy.tracker.TrailingStopTracker;
import tradingbot.config.TradingConfig;

public class LiquidationRiskExit implements PositionExitCondition {
    private final FuturesExchangeService exchangeService;
    private final TrailingStopTracker tracker;
    private final TradingConfig config;

    public LiquidationRiskExit(FuturesExchangeService exchangeService, TrailingStopTracker tracker, TradingConfig config) {
        this.exchangeService = exchangeService;
        this.tracker = tracker;
        this.config = config;
    }

    @Override
    public boolean shouldExit() {
        if (!"long".equals(tracker.getPosition())) {
            return false;
        }
        double price = exchangeService.getCurrentPrice(config.getSymbol());
        double entryPrice = tracker.getEntryPrice();
        double liquidationPrice = entryPrice * (1 - 1.0 / config.getLeverage());
        return price <= liquidationPrice * 1.05;
    }
}