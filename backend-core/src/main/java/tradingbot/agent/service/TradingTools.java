package tradingbot.agent.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.langchain4j.agent.tool.Tool;
import tradingbot.agent.domain.model.Order;
import tradingbot.agent.domain.model.TradeDirection;
import tradingbot.agent.domain.model.TradeOutcome;
import tradingbot.agent.infrastructure.persistence.PositionEntity;
import tradingbot.agent.config.AgentExecutionContext;

/**
 * TradingTools - LangChain4j tools for autonomous trading agent
 * 
 * This class provides tools that the LLM-powered agent can invoke
 * to gather information and execute trading actions.
 * 
 * Each @Tool method:
 * - Has a descriptive name for the LLM
 * - Is automatically exposed as a function the agent can call
 * - Returns structured data the agent can reason about
 */
@Component
public class TradingTools {
    
    private static final Logger logger = LoggerFactory.getLogger(TradingTools.class);
    
    private final AgentExecutionContext executionContext;
    private final OrderPlacementService orderPlacementService;
    private final PositionMonitoringService positionMonitoringService;
    private final RAGService ragService;

    @Value("${rag.order.dry-run:true}")
    private boolean dryRun;

    @Value("${rag.order.max-position-size-percent:10}")
    private double maxPositionSizePercent;

    public TradingTools(
            AgentExecutionContext executionContext,
            OrderPlacementService orderPlacementService,
            PositionMonitoringService positionMonitoringService,
            RAGService ragService) {
        this.executionContext = executionContext;
        this.orderPlacementService = orderPlacementService;
        this.positionMonitoringService = positionMonitoringService;
        this.ragService = ragService;
    }
    
    /**
     * Get current market price for a trading symbol
     */
    @Tool("Get the current market price for a trading symbol (e.g., BTCUSDT)")
    public double getCurrentPrice(String symbol) {
        try {
            logger.info("Tool called: getCurrentPrice for {}", symbol);
            double price = executionContext.get().getCurrentPrice(symbol);
            logger.info("Current price for {}: {}", symbol, price);
            return price;
        } catch (Exception e) {
            logger.error("Error fetching price for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Get 24-hour volume for a symbol
     */
    @Tool("Get 24-hour trading volume for a symbol")
    public double get24HourVolume(String symbol) {
        try {
            logger.info("Tool called: get24HourVolume for {}", symbol);
            tradingbot.bot.service.Ticker24hrStats stats = executionContext.get().get24HourStats(symbol);
            double volume = stats.getVolume();
            logger.info("24h volume for {}: {}", symbol, volume);
            return volume;
        } catch (Exception e) {
            logger.error("Error fetching volume for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Get 24-hour price change percentage
     */
    @Tool("Get 24-hour price change percentage for a symbol")
    public double get24HourPriceChange(String symbol) {
        try {
            logger.info("Tool called: get24HourPriceChange for {}", symbol);
            tradingbot.bot.service.Ticker24hrStats stats = executionContext.get().get24HourStats(symbol);
            double priceChangePercent = stats.getPriceChangePercent();
            logger.info("24h price change for {}: {}%", symbol, priceChangePercent);
            return priceChangePercent;
        } catch (Exception e) {
            logger.error("Error fetching price change for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Calculate technical indicator - RSI
     */
    @Tool("Calculate RSI (Relative Strength Index) for a symbol. Returns value between 0-100. Above 70 is overbought, below 30 is oversold")
    public double calculateRSI(String symbol, int period) {
        try {
            logger.info("Tool called: calculateRSI for {} with period {}", symbol, period);
            
            // Fetch historical candles - need at least period + 1 candles
            int requiredCandles = period + 50; // Extra candles for warmup
            var candles = executionContext.get().fetchOhlcv(symbol, "15m", requiredCandles);
            
            if (candles.isEmpty()) {
                logger.warn("No candles available for RSI calculation");
                return 50.0; // Neutral
            }
            
            // Convert to TA4j BarSeries
            org.ta4j.core.BaseBarSeriesBuilder seriesBuilder = new org.ta4j.core.BaseBarSeriesBuilder();
            seriesBuilder.withName(symbol);
            org.ta4j.core.BarSeries series = seriesBuilder.build();
            
            for (var candle : candles) {
                series.addBar(
                    java.time.ZonedDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(candle.getOpenTime()),
                        java.time.ZoneId.systemDefault()
                    ),
                    candle.getOpen(),
                    candle.getHigh(),
                    candle.getLow(),
                    candle.getClose(),
                    candle.getVolume()
                );
            }
            
            // Calculate RSI using TA4j
            org.ta4j.core.indicators.RSIIndicator rsi = new org.ta4j.core.indicators.RSIIndicator(
                new org.ta4j.core.indicators.helpers.ClosePriceIndicator(series),
                period
            );
            
            double rsiValue = rsi.getValue(series.getEndIndex()).doubleValue();
            logger.info("RSI for {}: {}", symbol, rsiValue);
            return rsiValue;
            
        } catch (Exception e) {
            logger.error("Error calculating RSI for {}: {}", symbol, e.getMessage());
            return 50.0; // Neutral
        }
    }
    
    /**
     * Get market trend analysis
     */
    @Tool("Analyze market trend for a symbol. Returns UPTREND, DOWNTREND, or SIDEWAYS")
    public String getMarketTrend(String symbol) {
        try {
            logger.info("Tool called: getMarketTrend for {}", symbol);
            
            // Fetch historical candles for trend analysis
            var candles = executionContext.get().fetchOhlcv(symbol, "1h", 200);
            
            if (candles.isEmpty() || candles.size() < 50) {
                logger.warn("Insufficient candles for trend analysis");
                return "UNKNOWN";
            }
            
            // Convert to TA4j BarSeries
            org.ta4j.core.BaseBarSeriesBuilder seriesBuilder = new org.ta4j.core.BaseBarSeriesBuilder();
            seriesBuilder.withName(symbol);
            org.ta4j.core.BarSeries series = seriesBuilder.build();
            
            for (var candle : candles) {
                series.addBar(
                    java.time.ZonedDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(candle.getOpenTime()),
                        java.time.ZoneId.systemDefault()
                    ),
                    candle.getOpen(),
                    candle.getHigh(),
                    candle.getLow(),
                    candle.getClose(),
                    candle.getVolume()
                );
            }
            
            // Calculate EMAs for trend analysis
            org.ta4j.core.indicators.helpers.ClosePriceIndicator closePrice = 
                new org.ta4j.core.indicators.helpers.ClosePriceIndicator(series);
            org.ta4j.core.indicators.EMAIndicator ema20 = 
                new org.ta4j.core.indicators.EMAIndicator(closePrice, 20);
            org.ta4j.core.indicators.EMAIndicator ema50 = 
                new org.ta4j.core.indicators.EMAIndicator(closePrice, 50);
            
            double ema20Value = ema20.getValue(series.getEndIndex()).doubleValue();
            double ema50Value = ema50.getValue(series.getEndIndex()).doubleValue();
            
            // Determine trend based on EMA crossover
            String trend;
            double difference = Math.abs(ema20Value - ema50Value) / ema50Value * 100;
            
            if (ema20Value > ema50Value * 1.01) { // 1% threshold for clear uptrend
                trend = "UPTREND";
            } else if (ema20Value < ema50Value * 0.99) { // 1% threshold for clear downtrend
                trend = "DOWNTREND";
            } else {
                trend = "SIDEWAYS";
            }
            
            logger.info("Market trend for {}: {} (EMA20: {}, EMA50: {}, diff: {}%)", 
                symbol, trend, ema20Value, ema50Value, difference);
            return trend;
            
        } catch (Exception e) {
            logger.error("Error analyzing trend for {}: {}", symbol, e.getMessage());
            return "UNKNOWN";
        }
    }
    
    /**
     * Place a market buy order
     */
    @Tool("Place a market BUY order for a symbol with specified quantity. Returns order ID if successful")
    public String placeBuyOrder(String symbol, double quantity, Double stopLoss, Double takeProfit) {
        logger.info("Tool called: placeBuyOrder - {} qty={} stopLoss={} takeProfit={}", 
                   symbol, quantity, stopLoss, takeProfit);
        
        if (dryRun) {
            String orderId = UUID.randomUUID().toString();
            logger.info("[DRY RUN] Would place BUY order: {} qty={} - Order ID: {}", 
                       symbol, quantity, orderId);
            return String.format("DRY_RUN_ORDER_%s", orderId);
        }
        
        try {
            // Create order object
            Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .symbol(symbol)
                .direction(TradeDirection.LONG)
                .quantity(quantity)
                .stopLoss(stopLoss)
                .takeProfit(takeProfit)
                .status(Order.OrderStatus.PENDING)
                .createdAt(Instant.now())
                .build();
            
            // TODO: Execute order through exchange service
            logger.info("Placed BUY order: {}", order.getId());
            return order.getId();
        } catch (Exception e) {
            logger.error("Error placing buy order: {}", e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }
    
    /**
     * Place a market sell order
     */
    @Tool("Place a market SELL order for a symbol with specified quantity. Returns order ID if successful")
    public String placeSellOrder(String symbol, double quantity, Double stopLoss, Double takeProfit) {
        logger.info("Tool called: placeSellOrder - {} qty={} stopLoss={} takeProfit={}", 
                   symbol, quantity, stopLoss, takeProfit);
        
        if (dryRun) {
            String orderId = UUID.randomUUID().toString();
            logger.info("[DRY RUN] Would place SELL order: {} qty={} - Order ID: {}", 
                       symbol, quantity, orderId);
            return String.format("DRY_RUN_ORDER_%s", orderId);
        }
        
        try {
            // Create order object
            Order order = Order.builder()
                .id(UUID.randomUUID().toString())
                .symbol(symbol)
                .direction(TradeDirection.SHORT)
                .quantity(quantity)
                .stopLoss(stopLoss)
                .takeProfit(takeProfit)
                .status(Order.OrderStatus.PENDING)
                .createdAt(Instant.now())
                .build();
            
            // TODO: Execute order through exchange service
            logger.info("Placed SELL order: {}", order.getId());
            return order.getId();
        } catch (Exception e) {
            logger.error("Error placing sell order: {}", e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }
    
    /**
     * Get current account balance
     */
    @Tool("Get current available trading balance in USDT")
    public double getAvailableBalance() {
        try {
            logger.info("Tool called: getAvailableBalance");
            double balance = executionContext.get().getMarginBalance();
            logger.info("Available balance: {} USDT", balance);
            return balance;
        } catch (Exception e) {
            logger.error("Error fetching balance: {}", e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Calculate position size based on risk percentage
     */
    @Tool("Calculate recommended position size based on account balance and risk percentage (1-10%)")
    public double calculatePositionSize(double accountBalance, double riskPercent, double entryPrice, double stopLoss) {
        logger.info("Tool called: calculatePositionSize - balance={} risk={}% entry={} stop={}", 
                   accountBalance, riskPercent, entryPrice, stopLoss);
        
        if (riskPercent < 0 || riskPercent > maxPositionSizePercent) {
            logger.warn("Risk percentage {}% exceeds maximum allowed {}%", 
                       riskPercent, maxPositionSizePercent);
            riskPercent = Math.min(riskPercent, maxPositionSizePercent);
        }
        
        double riskAmount = accountBalance * (riskPercent / 100.0);
        double stopDistance = Math.abs(entryPrice - stopLoss);
        
        if (stopDistance == 0) {
            logger.warn("Stop loss equals entry price, using default 2% stop");
            stopDistance = entryPrice * 0.02;
        }
        
        double positionSize = riskAmount / stopDistance;
        logger.info("Calculated position size: {} units", positionSize);
        
        return positionSize;
    }
    
    /**
     * Check if it's a good time to trade (avoid low liquidity periods)
     */
    @Tool("Check if current time is optimal for trading based on market hours and liquidity")
    public boolean isGoodTimeToTrade() {
        logger.info("Tool called: isGoodTimeToTrade");
        try {
            // Check if volume is sufficient (use BTCUSDT as market reference)
            tradingbot.bot.service.Ticker24hrStats stats = executionContext.get().get24HourStats("BTCUSDT");
            double volume = stats.getVolume();
            
            // Consider it good time to trade if 24h volume > 10,000 BTC (indicating active market)
            boolean isGoodTime = volume > 10000.0;
            
            logger.info("Good time to trade: {} (BTCUSDT 24h volume: {})", isGoodTime, volume);
            return isGoodTime;
            
        } catch (Exception e) {
            logger.warn("Could not check trading conditions: {}", e.getMessage());
            // Default to true for crypto (24/7 markets)
            return true;
        }
    }

    // ── Risk, Position, and Learning Tools ────────────────────────────────────

    /**
     * Evaluate risk for a proposed new trade entry.
     * Checks that the position size does not exceed the configured maximum percentage
     * of the available account balance.
     */
    @Tool("Evaluate risk for a proposed trade. Returns APPROVE or BLOCK with reason. positionSizeUsdt is the USDT value of the trade.")
    public String evaluateRisk(String symbol, double positionSizeUsdt) {
        logger.info("Tool called: evaluateRisk - {} positionSizeUsdt={}", symbol, positionSizeUsdt);
        try {
            if (positionSizeUsdt <= 0) {
                return "BLOCK: Position size must be positive.";
            }
            double balance = executionContext.get().getMarginBalance();
            if (balance <= 0) {
                return "BLOCK: Unable to retrieve account balance — cannot evaluate risk.";
            }
            double maxAllowed = balance * (maxPositionSizePercent / 100.0);
            if (positionSizeUsdt > maxAllowed) {
                return String.format(
                    "BLOCK: Position size $%.2f exceeds max allowed $%.2f (%.0f%% of $%.2f balance). Reduce to $%.2f or less.",
                    positionSizeUsdt, maxAllowed, maxPositionSizePercent, balance, maxAllowed);
            }
            return String.format(
                "APPROVE: Position size $%.2f is within risk limits (%.0f%% of $%.2f balance). Max allowed: $%.2f.",
                positionSizeUsdt, maxPositionSizePercent, balance, maxAllowed);
        } catch (Exception e) {
            logger.error("Error evaluating risk for {}: {}", symbol, e.getMessage());
            return "BLOCK: Risk evaluation failed — " + e.getMessage();
        }
    }

    /**
     * Return all currently open positions for the given agent as a formatted string.
     */
    @Tool("Get all currently open positions for an agent. Returns symbol, side, entry price, quantity, and unrealised P&L.")
    public String getOpenPositions(String agentId) {
        logger.info("Tool called: getOpenPositions agentId={}", agentId);
        try {
            List<PositionEntity> positions = positionMonitoringService.getOpenPositions(agentId);
            if (positions.isEmpty()) {
                return "No open positions for agent " + agentId + ".";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Open positions for agent ").append(agentId).append(":\n");
            for (PositionEntity p : positions) {
                sb.append(String.format("  - %s %s | entry=%.4f | qty=%.6f | unrealisedPnL=$%.2f%n",
                    p.getDirection(), p.getSymbol(),
                    p.getEntryPrice(), p.getQuantity(),
                    p.getLastUnrealizedPnl()));
            }
            return sb.toString().trim();
        } catch (Exception e) {
            logger.error("Error fetching open positions for {}: {}", agentId, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Record the final outcome of a completed trade in the RAG memory store.
     * outcome must be one of: WIN (or PROFIT), LOSS, BREAKEVEN.
     */
    @Tool("Record the final outcome of a completed trade for future learning. outcome must be WIN, LOSS, or BREAKEVEN.")
    public void recordTradeOutcome(String symbol, String outcome, double profitPercent, String lessonLearned) {
        logger.info("Tool called: recordTradeOutcome - {} outcome={} profit={}%", symbol, outcome, profitPercent);
        try {
            // Normalise caller-friendly 'WIN' to the enum value 'PROFIT'
            String normalised = "WIN".equalsIgnoreCase(outcome) ? "PROFIT" : outcome.toUpperCase();
            TradeOutcome tradeOutcome = TradeOutcome.valueOf(normalised);
            ragService.storeTradeMemory(
                "tool_direct",                            // agentId — direct LLM invocation
                symbol,
                "LLM tool call: " + normalised + " on " + symbol,
                TradeDirection.LONG,                      // direction unknown at this call site
                0.0,                                      // entryPrice — not available here
                null,                                     // exitPrice  — not available here
                tradeOutcome,
                profitPercent,
                lessonLearned,
                null                                      // networkFee
            );
            logger.info("Trade outcome recorded for {}: {} ({:+.2f}%)", symbol, normalised, profitPercent);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid outcome value '{}' — must be WIN, LOSS, or BREAKEVEN", outcome);
        } catch (Exception e) {
            logger.error("Error recording trade outcome for {}: {}", symbol, e.getMessage());
        }
    }
}
