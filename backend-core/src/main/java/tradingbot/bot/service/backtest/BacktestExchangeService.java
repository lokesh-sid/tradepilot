package tradingbot.bot.service.backtest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.OrderResult;
import tradingbot.bot.service.Ticker24hrStats;

public class BacktestExchangeService implements FuturesExchangeService {
    private static final Logger logger = LoggerFactory.getLogger(BacktestExchangeService.class);

    private double marginBalance = 10000.0;
    private int leverage = 1;
    private Map<String, Double> positions = new HashMap<>();
    private Map<String, Double> entryPrices = new HashMap<>();
    
    private Candle currentCandle;
    private long currentTime;
    private List<Candle> history;
    private int currentIndex;
    
    private final long latencyMs;
    private final double takerFeeRate;
    
    private Queue<PendingOrder> pendingOrders = new LinkedList<>();
    
    private final AtomicLong orderIdGenerator = new AtomicLong(1);

    private static final String LONG_SUFFIX = ":LONG";
    private static final String SHORT_SUFFIX = ":SHORT";

    public BacktestExchangeService(long latencyMs, double slippagePercent, double takerFeeRate) {
        this.latencyMs = latencyMs;
        this.takerFeeRate = takerFeeRate;
    }

    public void setMarketContext(List<Candle> history, int currentIndex) {
        this.history = history;
        this.currentIndex = currentIndex;
        this.currentCandle = history.get(currentIndex);
        this.currentTime = currentCandle.getCloseTime(); 
        
        // Phase 2: Environment Check
        checkLiquidations();
        
        // Phase 3: Order Execution
        processPendingOrders();
    }

    private void checkLiquidations() {
        double low = currentCandle.getLow().doubleValue();
        double high = currentCandle.getHigh().doubleValue();
        List<String> liquidatedKeys = collectLiquidatedKeys(low, high);
        for (String key : liquidatedKeys) {
            positions.remove(key);
            entryPrices.remove(key);
            logger.info("LIQUIDATION on {} at {}", key, (key.endsWith(LONG_SUFFIX) ? low : high));
        }
    }

    private List<String> collectLiquidatedKeys(double low, double high) {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Double> entrySet : positions.entrySet()) {
            String key = entrySet.getKey();
            Double entryPrice = entryPrices.get(key);
            if (entryPrice == null) {
                continue;
            }
            if (key.endsWith(LONG_SUFFIX)) {
                double liquidationPrice = entryPrice * (1 - (1.0 / leverage));
                if (low <= liquidationPrice) {
                    keys.add(key);
                }
            } else if (key.endsWith(SHORT_SUFFIX)) {
                double liquidationPrice = entryPrice * (1 + (1.0 / leverage));
                if (high >= liquidationPrice) {
                    keys.add(key);
                }
            }
        }
        return keys;
    }
    
    public void processPendingOrders() {
        while (!pendingOrders.isEmpty()) {
            PendingOrder order = pendingOrders.peek();
            
            // Check 1: Latency simulation (Time must have passed)
            if (currentTime < order.executionTime) {
                break; 
            }
            
            // Check 2: Price Reachability (Simple simulation)
            // For now, we simply execute assuming market orders or valid fills
            pendingOrders.poll();
            executeOrder(order);
        }
    }

    private void executeOrder(PendingOrder order) {
        double price = calculateExecutionPrice(order);
        double tradeValue = order.amount * price;
        double fee = tradeValue * takerFeeRate;

        if (order.isEntry) {
            handleEntry(order, price, tradeValue, fee);
        } else {
            handleExit(order, price, fee);
        }
    }

    private double calculateExecutionPrice(PendingOrder order) {
        // Use Open price as the base for execution (Simulating 'Next Candle Open' execution)
        double price = currentCandle.getOpen().doubleValue();
        
        // 1. Generate Realistic Slippage (random between 0.05% and 0.1%)
        // This simulates price movement in the seconds/milliseconds after open
        double randomSlippage = ThreadLocalRandom.current().nextDouble(0.0005, 0.001);

        // 2. Apply Slippage against the trade direction (Buy High, Sell Low)
        if (order.type == OrderType.BUY) {
            return price * (1 + randomSlippage);
        } else {
            return price * (1 - randomSlippage);
        }
    }

    private void handleEntry(PendingOrder order, double price, double tradeValue, double fee) {
        // ENTRY LOGIC
        double initialMargin = tradeValue / leverage;
        if (marginBalance >= (initialMargin + fee)) {
            marginBalance -= (initialMargin + fee);
            String key = order.symbol + (order.type == OrderType.BUY ? LONG_SUFFIX : SHORT_SUFFIX);
            positions.put(key, order.amount);
            entryPrices.put(key, price);
        }
    }

    private void handleExit(PendingOrder order, double price, double fee) {
        // EXIT LOGIC
        String key = order.symbol + (order.type == OrderType.SELL ? LONG_SUFFIX : SHORT_SUFFIX);
        Double entryPrice = entryPrices.get(key);
        if (entryPrice != null) {
            double pnl;
            double initialMargin = (order.amount * entryPrice) / leverage;

            if (order.type == OrderType.SELL) { // Closing Long
                pnl = (price - entryPrice) * order.amount;
            } else { // Closing Short
                pnl = (entryPrice - price) * order.amount;
            }

            marginBalance += (initialMargin + pnl - fee);
            positions.remove(key);
            entryPrices.remove(key);
        }
    }

    @Override
    public List<Candle> fetchOhlcv(String symbol, String timeframe, int limit) {
        int start = Math.max(0, currentIndex - limit + 1);
        return new ArrayList<>(history.subList(start, currentIndex + 1));
    }

    @Override
    public double getCurrentPrice(String symbol) {
        return currentCandle.getClose().doubleValue();
    }

    @Override
    public double getMarginBalance() {
        return marginBalance;
    }
    
    @Override
    public Ticker24hrStats get24HourStats(String symbol) {
        // Calculate 24h stats from historical data (last 24 candles for 1h timeframe)
        int lookback = Math.min(24, currentIndex + 1);
        List<Candle> recentCandles = history.subList(Math.max(0, currentIndex - lookback + 1), currentIndex + 1);
        
        double high = recentCandles.stream().mapToDouble(c -> c.getHigh().doubleValue()).max().orElse(0.0);
        double low = recentCandles.stream().mapToDouble(c -> c.getLow().doubleValue()).min().orElse(0.0);
        double volume = recentCandles.stream().mapToDouble(c -> c.getVolume().doubleValue()).sum();
        double open = recentCandles.get(0).getOpen().doubleValue();
        double last = currentCandle.getClose().doubleValue();
        double priceChange = last - open;
        double priceChangePercent = (priceChange / open) * 100;
        
        return Ticker24hrStats.builder()
            .symbol(symbol)
            .volume(volume)
            .quoteVolume(volume * last)
            .priceChange(priceChange)
            .priceChangePercent(priceChangePercent)
            .highPrice(high)
            .lowPrice(low)
            .openPrice(open)
            .lastPrice(last)
            .build();
    }

    @Override
    public void setLeverage(String symbol, int leverage) {
        this.leverage = leverage;
    }

    private double normalizeQuantity(double quantity) {
        double stepSize = 0.001; 
        return Math.floor(quantity / stepSize) * stepSize;
    }

    @Override
    public OrderResult enterLongPosition(String symbol, double tradeAmount) {
        double quantity = normalizeQuantity(tradeAmount);
        pendingOrders.add(new PendingOrder(symbol, quantity, OrderType.BUY, true, currentTime + latencyMs));
        
        String orderId = "BT-" + orderIdGenerator.getAndIncrement();
        return OrderResult.builder()
            .exchangeOrderId(orderId)
            .clientOrderId(orderId)
            .symbol(symbol)
            .side("BUY")
            .status(OrderResult.OrderStatus.NEW)
            .orderedQuantity(quantity)
            .filledQuantity(0.0)
            .avgFillPrice(0.0)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Override
    public OrderResult exitLongPosition(String symbol, double tradeAmount) {
        double quantity = normalizeQuantity(tradeAmount);
        pendingOrders.add(new PendingOrder(symbol, quantity, OrderType.SELL, false, currentTime + latencyMs));
        
        String orderId = "BT-" + orderIdGenerator.getAndIncrement();
        return OrderResult.builder()
            .exchangeOrderId(orderId)
            .clientOrderId(orderId)
            .symbol(symbol)
            .side("SELL")
            .status(OrderResult.OrderStatus.NEW)
            .orderedQuantity(quantity)
            .filledQuantity(0.0)
            .avgFillPrice(0.0)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Override
    public OrderResult enterShortPosition(String symbol, double tradeAmount) {
        double quantity = normalizeQuantity(tradeAmount);
        pendingOrders.add(new PendingOrder(symbol, quantity, OrderType.SELL, true, currentTime + latencyMs));
        
        String orderId = "BT-" + orderIdGenerator.getAndIncrement();
        return OrderResult.builder()
            .exchangeOrderId(orderId)
            .clientOrderId(orderId)
            .symbol(symbol)
            .side("SELL")
            .status(OrderResult.OrderStatus.NEW)
            .orderedQuantity(quantity)
            .filledQuantity(0.0)
            .avgFillPrice(0.0)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Override
    public OrderResult exitShortPosition(String symbol, double tradeAmount) {
        double quantity = normalizeQuantity(tradeAmount);
        pendingOrders.add(new PendingOrder(symbol, quantity, OrderType.BUY, false, currentTime + latencyMs));
        
        String orderId = "BT-" + orderIdGenerator.getAndIncrement();
        return OrderResult.builder()
            .exchangeOrderId(orderId)
            .clientOrderId(orderId)
            .symbol(symbol)
            .side("BUY")
            .status(OrderResult.OrderStatus.NEW)
            .orderedQuantity(quantity)
            .filledQuantity(0.0)
            .avgFillPrice(0.0)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
    
    @Override
    public OrderResult placeStopLossOrder(String symbol, String side, double quantity, double stopPrice) {
        // For backtest, we'll simulate stop-loss by simply returning a pending order
        String orderId = "BT-SL-" + orderIdGenerator.getAndIncrement();
        return OrderResult.builder()
            .exchangeOrderId(orderId)
            .clientOrderId(orderId)
            .symbol(symbol)
            .side(side)
            .status(OrderResult.OrderStatus.NEW)
            .orderedQuantity(normalizeQuantity(quantity))
            .filledQuantity(0.0)
            .avgFillPrice(0.0)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
    
    @Override
    public OrderResult placeTakeProfitOrder(String symbol, String side, double quantity, double takeProfitPrice) {
        // For backtest, we'll simulate take-profit by simply returning a pending order
        String orderId = "BT-TP-" + orderIdGenerator.getAndIncrement();
        return OrderResult.builder()
            .exchangeOrderId(orderId)
            .clientOrderId(orderId)
            .symbol(symbol)
            .side(side)
            .status(OrderResult.OrderStatus.NEW)
            .orderedQuantity(normalizeQuantity(quantity))
            .filledQuantity(0.0)
            .avgFillPrice(0.0)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
    
    private static class PendingOrder {
        String symbol;
        double amount;
        OrderType type;
        boolean isEntry;
        long executionTime;

        public PendingOrder(String symbol, double amount, OrderType type, boolean isEntry, long executionTime) {
            this.symbol = symbol;
            this.amount = amount;
            this.type = type;
            this.isEntry = isEntry;
            this.executionTime = executionTime;
        }
    }
    
    private enum OrderType { BUY, SELL }
}
