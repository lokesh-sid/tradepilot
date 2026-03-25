package tradingbot.bot.strategy.tracker;

import java.util.logging.Logger;

import tradingbot.bot.service.FuturesExchangeService;

public class TrailingStopTracker {
    private static final Logger LOGGER = Logger.getLogger(TrailingStopTracker.class.getName());

    private final FuturesExchangeService exchangeService;
    private final double trailingStopPercent;
    private String position;
    private double entryPrice;
    private double highestPrice;

    public TrailingStopTracker(FuturesExchangeService exchangeService, double trailingStopPercent) {
        this.exchangeService = exchangeService;
        this.trailingStopPercent = trailingStopPercent;
        this.position = null;
        this.entryPrice = 0.0;
        this.highestPrice = 0.0;
    }

    public void initializeTrailingStop(double price) {
        this.highestPrice = price;
        this.entryPrice = price;
        this.position = "long";
        LOGGER.info(() -> "Trailing stop initialized at %.2f".formatted(price));
    }

    public void reset() {
        this.highestPrice = 0.0;
        this.entryPrice = 0.0;
        this.position = null;
    }

    public void updateTrailingStop(double price) {
        if ("long".equals(position) && price > highestPrice) {
            highestPrice = price;
            LOGGER.info(() ->"Updated trailing stop: New highest price %.2f".formatted(highestPrice));
        }
    }

    public boolean checkTrailingStop(double price) {
        if (!"long".equals(position) || highestPrice <= 0) {
            return false;
        }
        double stopPrice = highestPrice * (1 - trailingStopPercent / 100);
        if (price <= stopPrice) {
            LOGGER.info(() -> "Trailing stop-loss triggered! Price: %.2f, Stop price: %.2f".formatted(price, stopPrice));
            return true;
        }
        return false;
    }

    public double getCurrentPrice() {
        return exchangeService.getCurrentPrice("BTCUSDT");
    }

    public double getHighestPrice() {
        return highestPrice;
    }

    public String getPosition() {
        return position;
    }

    public double getEntryPrice() {
        return entryPrice;
    }
}