package tradingbot.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import tradingbot.agent.TradingAgent;
import tradingbot.bot.capability.LeverageConfigurable;
import tradingbot.bot.capability.SentimentAware;
import tradingbot.bot.events.MarketDataEvent;
import tradingbot.bot.model.MarketData;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.bot.strategy.calculator.IndicatorCalculator;
import tradingbot.bot.strategy.calculator.IndicatorValues;
import tradingbot.bot.strategy.exit.PositionExitCondition;
import tradingbot.bot.strategy.tracker.TrailingStopTracker;
import tradingbot.config.TradingConfig;

public class FuturesTradingBot implements TradingAgent, LeverageConfigurable, SentimentAware {
    private final Logger logger = Logger.getLogger(FuturesTradingBot.class.getName());
    private static final int CHECK_INTERVAL_SECONDS = 900; // 15 minutes

    private final String id;
    private final String name;
    private FuturesExchangeService exchangeService;
    private IndicatorCalculator indicatorCalculator;
    private TrailingStopTracker trailingStopTracker;
    private SentimentAnalyzer sentimentAnalyzer;
    private List<PositionExitCondition> exitConditions;
    private TradingConfig config;
    private TradeDirection direction;
    private String positionStatus;
    private double entryPrice;
    private volatile boolean running;
    private volatile boolean sentimentEnabled;
    private int currentLeverage;

    // Public getters for accessibility
    public FuturesExchangeService getExchangeService() { return exchangeService; }
    public IndicatorCalculator getIndicatorCalculator() { return indicatorCalculator; }
    public TrailingStopTracker getTrailingStopTracker() { return trailingStopTracker; }
    public SentimentAnalyzer getSentimentAnalyzer() { return sentimentAnalyzer; }
    public List<PositionExitCondition> getExitConditions() { return exitConditions; }
    public TradingConfig getConfig() { return config; }
    public TradeDirection getDirection() { return direction; }
    public String getPositionStatus() { return positionStatus; }
    public double getEntryPrice() { return entryPrice; }
    public boolean isRunning() { return running; }
    public boolean isSentimentEnabled() { return sentimentEnabled; }
    public int getCurrentLeverage() { return currentLeverage; }

    public FuturesTradingBot(BotParams params) {
        this.id = params.id != null ? params.id : java.util.UUID.randomUUID().toString();
        this.name = params.name != null ? params.name : "FuturesBot-" + this.id.substring(0, 8);
        this.exchangeService = params.exchangeService;
        this.indicatorCalculator = params.indicatorCalculator;
        this.trailingStopTracker = params.trailingStopTracker;
        this.sentimentAnalyzer = params.sentimentAnalyzer;
        this.exitConditions = params.exitConditions;
        this.config = params.config;
        this.direction = params.direction;
        this.positionStatus = null;
        this.entryPrice = 0.0;
        this.running = false;
        this.sentimentEnabled = false;
        this.currentLeverage = config.getLeverage();
        if (!params.skipLeverageInit) {
            initializeLeverage();
        }
        logInitialization();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }


    public static class BotParams {
        private final String id;
        private final String name;
        private final FuturesExchangeService exchangeService;
        private final IndicatorCalculator indicatorCalculator;
        private final TrailingStopTracker trailingStopTracker;
        private final SentimentAnalyzer sentimentAnalyzer;
        private final List<PositionExitCondition> exitConditions;
        private final TradingConfig config;
        private final TradeDirection direction;
        private final boolean skipLeverageInit;

        private BotParams(Builder builder) {
            this.id = builder.id;
            this.name = builder.name;
            this.exchangeService = builder.exchangeService;
            this.indicatorCalculator = builder.indicatorCalculator;
            this.trailingStopTracker = builder.trailingStopTracker;
            this.sentimentAnalyzer = builder.sentimentAnalyzer;
            this.exitConditions = List.copyOf(builder.exitConditions); // Defensive copy
            this.config = builder.config;
            this.direction = builder.direction;
            this.skipLeverageInit = builder.skipLeverageInit;
        }

        public String getId() { return id; }
        public String getName() { return name; }

        public FuturesExchangeService getExchangeService() {
            return exchangeService;
        }

        public IndicatorCalculator getIndicatorCalculator() {
            return indicatorCalculator;
        }

        public TrailingStopTracker getTrailingStopTracker() {
            return trailingStopTracker;
        }

        public SentimentAnalyzer getSentimentAnalyzer() {
            return sentimentAnalyzer;
        }

        public List<PositionExitCondition> getExitConditions() {
            return exitConditions; // Already immutable from defensive copy
        }

        public TradingConfig getConfig() {
            return config;
        }

        public TradeDirection getDirection() {
            return direction;
        }

        public boolean isSkipLeverageInit() {
            return skipLeverageInit;
        }

        public static class Builder {
            // Shared validator instance for better performance
            private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
            
            private String id;
            private String name;

            @NotNull(message = "Exchange service is required")
            private FuturesExchangeService exchangeService;
            
            @NotNull(message = "Indicator calculator is required")
            private IndicatorCalculator indicatorCalculator;
            
            @NotNull(message = "Trailing stop tracker is required")
            private TrailingStopTracker trailingStopTracker;
            
            @NotNull(message = "Sentiment analyzer is required")
            private SentimentAnalyzer sentimentAnalyzer;
            
            @NotEmpty(message = "Exit conditions are required and cannot be empty")
            private List<PositionExitCondition> exitConditions;
            
            @NotNull(message = "Trading config is required")
            private TradingConfig config;
            
            @NotNull(message = "Trade direction is required")
            private TradeDirection direction;
            
            private boolean skipLeverageInit = false; // Default value

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            /**
             * Sets the futures exchange service for executing trades.
             * @param exchangeService the exchange service (required)
             * @return this builder
             */
            public Builder exchangeService(FuturesExchangeService exchangeService) {
                this.exchangeService = exchangeService;
                return this;
            }

            /**
             * Sets the indicator calculator for technical analysis.
             * @param indicatorCalculator the indicator calculator (required)
             * @return this builder
             */
            public Builder indicatorCalculator(IndicatorCalculator indicatorCalculator) {
                this.indicatorCalculator = indicatorCalculator;
                return this;
            }

            /**
             * Sets the trailing stop tracker for risk management.
             * @param trailingStopTracker the trailing stop tracker (required)
             * @return this builder
             */
            public Builder trailingStopTracker(TrailingStopTracker trailingStopTracker) {
                this.trailingStopTracker = trailingStopTracker;
                return this;
            }

            /**
             * Sets the sentiment analyzer for market sentiment analysis.
             * @param sentimentAnalyzer the sentiment analyzer (required)
             * @return this builder
             */
            public Builder sentimentAnalyzer(SentimentAnalyzer sentimentAnalyzer) {
                this.sentimentAnalyzer = sentimentAnalyzer;
                return this;
            }

            /**
             * Sets the exit conditions for position management.
             * @param exitConditions the list of exit conditions (required, non-empty)
             * @return this builder
             * @throws IllegalArgumentException if exit conditions list is empty
             */
            public Builder exitConditions(List<PositionExitCondition> exitConditions) {
                if (exitConditions != null && exitConditions.isEmpty()) {
                    throw new IllegalArgumentException("Exit conditions cannot be empty");
                }
                this.exitConditions = exitConditions != null ? new ArrayList<>(exitConditions) : null;
                return this;
            }

            /**
             * Sets the trading configuration.
             * @param config the trading configuration (required)
             * @return this builder
             */
            public Builder config(TradingConfig config) {
                this.config = config;
                return this;
            }

            /**
             * Sets the trade direction (LONG or SHORT).
             * @param direction the trade direction (required)
             * @return this builder
             */
            public Builder tradeDirection(TradeDirection direction) {
                this.direction = direction;
                return this;
            }

            /**
             * Sets whether to skip leverage initialization. 
             * Useful for testing environments or when leverage is managed externally.
             * @param skipLeverageInit true to skip leverage initialization, false otherwise (default: false)
             * @return this builder
             */
            public Builder skipLeverageInit(boolean skipLeverageInit) {
                this.skipLeverageInit = skipLeverageInit;
                return this;
            }

            /**
             * Convenience method for test mode - equivalent to skipLeverageInit(true).
             * @return this builder
             */
            public Builder forTesting() {
                return skipLeverageInit(true);
            }

            /**
             * Validates all required fields and builds the BotParams instance.
             * @return the configured BotParams
             * @throws IllegalStateException if any required field is missing or invalid
             */
            public BotParams build() {
                validateRequiredFields();
                return new BotParams(this);
            }

            private void validateRequiredFields() {
                // Use cached validator for better performance
                Set<ConstraintViolation<Builder>> violations = VALIDATOR.validate(this);
                
                if (!violations.isEmpty()) {
                    StringBuilder errorMessage = new StringBuilder("Validation failed: ");
                    for (ConstraintViolation<Builder> violation : violations) {
                        errorMessage.append(violation.getMessage()).append("; ");
                    }
                    
                    // Remove trailing "; "
                    if (errorMessage.length() > 2) {
                        errorMessage.setLength(errorMessage.length() - 2);
                    }
                    
                    throw new IllegalStateException(errorMessage.toString());
                }
            }
        }
    }

    @Override
    public void start() {
        if (running) {
            logger.warning("Trading bot is already running");
            return;
        }
        new Thread(this::run).start();
    }

    @Override
    public void stop() {
        running = false;
        if (isInPosition()) {
            exitPosition();
        }
    }

    @Override
    public void onEvent(Object event) {
        if (event instanceof MarketDataEvent) {
            // Treat MarketDataEvent as a trigger to fetch fresh analyzed data
            MarketData marketData = fetchMarketData();
            if (marketData != null) {
                processMarketData(marketData);
            }
        }
    }

    public void processMarketData(MarketData marketData) {
        // Invalidate or update cachedMarketData to avoid stale data
        cachedMarketData = marketData;
        logMarketData(exchangeService.getCurrentPrice(config.getSymbol()), marketData);
        if (!isInPosition() && isEntrySignalValid(marketData)) {
            enterPosition();
        }
    }

    @Override
    @Deprecated
    public void executeTrade() {
        MarketData marketData = fetchMarketData();
        if (marketData != null && !isInPosition() && isEntrySignalValid(marketData)) {
            enterPosition();
        }
    }

    public String getStatus() {
        return running ? "Running, Direction: " + direction + ", Position: " + (positionStatus != null ? positionStatus : "None") : "Stopped";
    }

    public void updateConfig(TradingConfig newConfig) {
        this.config = newConfig;
        initializeLeverage();
    logger.info("Configuration updated");
    }

    public void setDynamicLeverage(int newLeverage) {
        if (newLeverage < 1 || newLeverage > 125) {
            logger.severe(() -> "Invalid leverage value: " + newLeverage);
            throw new IllegalArgumentException("Leverage must be between 1 and 125");
        }
        this.currentLeverage = newLeverage;
        initializeLeverage();
        logger.info(() -> "Dynamic leverage set to %dx".formatted(newLeverage));
    }

    public void enableSentimentAnalysis(boolean enable) {
        this.sentimentEnabled = enable;
        logger.info(() -> "Sentiment analysis  " + (enable ? "enabled" : "disabled"));
    }

    private void initializeLeverage() {
        try {
            exchangeService.setLeverage(config.getSymbol(), currentLeverage);
            logger.info(() -> "Leverage set to %dx for %s".formatted(currentLeverage, config.getSymbol()));
        } catch (Exception e) {
            logger.severe("Failed to set leverage for symbol " + config.getSymbol() + " to " + currentLeverage + "x: " + e.getMessage());
            throw new IllegalArgumentException("Leverage initialization failed for " + config.getSymbol(), e);
        }
    }

    private void logInitialization() {
        logger.info(() -> "Bot initialized for %s %s with %dx leverage, trailing stop: %.2f%%".formatted(
                direction == TradeDirection.LONG ? "longing" : "shorting",
                config.getSymbol(), currentLeverage, config.getTrailingStopPercent()));
    }

    private void run() {
        running = true;
        while (running) {
            try {
                executeTradingStep();
                Thread.sleep(CHECK_INTERVAL_SECONDS * 1000);
            } catch (InterruptedException e) {
                logger.severe("Trading loop interrupted");
                Thread.currentThread().interrupt();
                running = false;
            } catch (Exception e) {
                logger.severe("Error in trading cycle: " + e.getMessage());
                sleepSafely();
            }
        }
    }

    public void executeTradingStep() {
        // Invalidate cache at the start of each step to ensure fresh data for backtesting
        cachedMarketData = null;
        
        double currentPrice = exchangeService.getCurrentPrice(config.getSymbol());
        trailingStopTracker.updateTrailingStop(currentPrice);

        if (isInPosition() && shouldExitPosition(currentPrice)) {
            exitPosition();
            return;
        }

        MarketData marketData = fetchMarketData();
        if (marketData == null) {
            return;
        }

        logMarketData(currentPrice, marketData);
        if (!isInPosition() && isEntrySignalValid(marketData)) {
            enterPosition();
        }
    }

    private boolean isInPosition() {
        if (direction == TradeDirection.LONG) {
            return "long".equals(positionStatus);
        } else {
            return "short".equals(positionStatus);
        }
    }

    private boolean shouldExitPosition(double currentPrice) {
        return trailingStopTracker.checkTrailingStop(currentPrice) ||
               exitConditions.stream().anyMatch(PositionExitCondition::shouldExit);
    }

    private MarketData cachedMarketData = null;
    private MarketData fetchMarketData() {
        if (cachedMarketData != null) {
            return cachedMarketData;
        }
        IndicatorValues dailyIndicators = indicatorCalculator.computeIndicators("1d", config.getSymbol());
        IndicatorValues weeklyIndicators = indicatorCalculator.computeIndicators("1w", config.getSymbol());
        if (dailyIndicators == null || weeklyIndicators == null) {
            logger.warning("Insufficient data for indicators");
            return null;
        }
        
        double currentPrice = exchangeService.getCurrentPrice(config.getSymbol());
        
        cachedMarketData = new MarketData(
            dailyIndicators, 
            weeklyIndicators, 
            currentPrice, 
            0.0, 
            0.0, 
            "UNKNOWN", 
            "UNKNOWN", 
            0.0
        );
        return cachedMarketData;
    }

    private void logMarketData(double price, MarketData marketData) {
        logger.info(() -> ("Price: %.2f, Daily RSI: %.2f, Daily MACD: %.2f, Daily Signal: %.2f, " +
                "Daily Lower BB: %.2f, Daily Upper BB: %.2f, Weekly RSI: %.2f, Highest Price: %.2f").formatted(
                price, marketData.dailyIndicators().getRsi(), marketData.dailyIndicators().getMacd(),
                marketData.dailyIndicators().getSignal(), marketData.dailyIndicators().getLowerBand(),
                marketData.dailyIndicators().getUpperBand(), marketData.weeklyIndicators().getRsi(),
                trailingStopTracker.getHighestPrice()));
    }

    private boolean isEntrySignalValid(MarketData marketData) {
        double currentPrice = exchangeService.getCurrentPrice(config.getSymbol());
        boolean technicalSignal;
        if (direction == TradeDirection.LONG) {
            technicalSignal = marketData.dailyIndicators().getRsi() <= config.getRsiOversoldThreshold() &&
                             marketData.dailyIndicators().getMacd() > marketData.dailyIndicators().getSignal() &&
                             currentPrice <= marketData.dailyIndicators().getLowerBand() * 1.01 &&
                             marketData.weeklyIndicators().getRsi() < config.getRsiOverboughtThreshold();
            if (sentimentEnabled) {
                return technicalSignal && sentimentAnalyzer.isPositiveSentiment(config.getSymbol());
            }
            return technicalSignal;
        } else {
            technicalSignal = marketData.dailyIndicators().getRsi() >= config.getRsiOverboughtThreshold() &&
                             marketData.dailyIndicators().getMacd() < marketData.dailyIndicators().getSignal() &&
                             currentPrice >= marketData.dailyIndicators().getUpperBand() * 0.99 &&
                             marketData.weeklyIndicators().getRsi() > config.getRsiOversoldThreshold();
            if (sentimentEnabled) {
                return technicalSignal && sentimentAnalyzer.isNegativeSentiment(config.getSymbol());
            }
            return technicalSignal;
        }
    }

    private void enterPosition() {
        double price = exchangeService.getCurrentPrice(config.getSymbol());
        double requiredMargin = config.getTradeAmount() * price / currentLeverage;

        if (exchangeService.getMarginBalance() < requiredMargin) {
            logger.warning(() -> "Insufficient margin balance (USDT) to %s %.4f %s with %dx leverage".formatted(
                    direction == TradeDirection.LONG ? "buy" : "sell",
                    config.getTradeAmount(), config.getSymbol(), currentLeverage));
            return;
        }

        try {
            if (direction == TradeDirection.LONG) {
                exchangeService.enterLongPosition(config.getSymbol(), config.getTradeAmount());
                logger.info(() ->"Entered long: Bought %.4f %s at %.2f with %dx leverage".formatted(
                        config.getTradeAmount(), config.getSymbol(), price, currentLeverage));
                positionStatus = "long";
            } else {
                exchangeService.enterShortPosition(config.getSymbol(), config.getTradeAmount());
                logger.info(() ->"Entered short: Sold %.4f %s at %.2f with %dx leverage".formatted(
                        config.getTradeAmount(), config.getSymbol(), price, currentLeverage));
                positionStatus = "short";
            }
            entryPrice = price;
            trailingStopTracker.initializeTrailingStop(price);
        } catch (Exception e) {
            logger.severe("Failed to enter " + direction.name().toLowerCase() + " position for " + config.getSymbol() + " with amount " + config.getTradeAmount() + ": " + e.getMessage());
            throw new PositionEntryException("Position entry failed for " + direction.name().toLowerCase() + " trade", e);
        }
    }

    private void exitPosition() {
        double price = exchangeService.getCurrentPrice(config.getSymbol());
        try {
            if (direction == TradeDirection.LONG) {
                exchangeService.exitLongPosition(config.getSymbol(), config.getTradeAmount());
                double profit = (price - entryPrice) * config.getTradeAmount() * currentLeverage;
                logger.info(() -> "Exited long: Sold %.4f %s at %.2f with %dx leverage, Profit: %.2f".formatted(
                        config.getTradeAmount(), config.getSymbol(), price, currentLeverage, profit));
            } else {
                exchangeService.exitShortPosition(config.getSymbol(), config.getTradeAmount());
                double profit = (entryPrice - price) * config.getTradeAmount() * currentLeverage;
                logger.info(() -> "Exited short: Bought %.4f %s at %.2f with %dx leverage, Profit: %.2f".formatted(
                        config.getTradeAmount(), config.getSymbol(), price, currentLeverage, profit));
            }
            positionStatus = null;
            entryPrice = 0.0;
            trailingStopTracker.reset();
        } catch (Exception e) {
            logger.severe("Failed to exit " + direction.name().toLowerCase() + " position for " + config.getSymbol() + " with amount " + config.getTradeAmount() + ": " + e.getMessage());
            throw new PositionExitException("Position exit failed for " + direction.name().toLowerCase() + " trade", e);
        }
    }

    private void sleepSafely() {
        try {
            Thread.sleep(CHECK_INTERVAL_SECONDS * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Exception thrown when a trading position entry fails
     */
    class PositionEntryException extends RuntimeException {
        public PositionEntryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when a trading position exit fails
     */
    class PositionExitException extends RuntimeException {
        public PositionExitException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
