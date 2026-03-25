package tradingbot.bot.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tradingbot.bot.controller.exception.BotOperationException;
import tradingbot.bot.service.BinanceFuturesService.Candle;

public class PaperFuturesExchangeService implements FuturesExchangeService {
    private double marginBalance = 10000.0; // Simulated USDT balance
    private int leverage = 1;
    private Map<String, Double> positions = new HashMap<>(); // symbol -> position size
    private Map<String, Double> entryPrices = new HashMap<>(); // symbol -> entry price
    private double lastPrice = 50000.0; // Simulated price

    @Override
    public List<Candle> fetchOhlcv(String symbol, String timeframe, int limit) {
        return List.of(); // No-op for paper trading
    }

    @Override
    public double getCurrentPrice(String symbol) {
        return lastPrice; // Simulated price
    }

    @Override
    public double getMarginBalance() {
        return marginBalance;
    }
    
    @Override
    public Ticker24hrStats get24HourStats(String symbol) {
        // Return simulated stats for paper trading
        return Ticker24hrStats.builder()
            .symbol(symbol)
            .volume(1000000.0)
            .quoteVolume(50000000000.0)
            .priceChange(100.0)
            .priceChangePercent(2.5)
            .highPrice(lastPrice * 1.02)
            .lowPrice(lastPrice * 0.98)
            .openPrice(lastPrice * 0.99)
            .lastPrice(lastPrice)
            .build();
    }

    @Override
    public void setLeverage(String symbol, int leverage) {
        this.leverage = leverage;
    }

    @Override
    public OrderResult enterLongPosition(String symbol, double tradeAmount) {
        double price = getCurrentPrice(symbol);
        double requiredMargin = tradeAmount * price / leverage;
        if (marginBalance < requiredMargin) throw new BotOperationException("enterLongPosition", "Insufficient margin");
        marginBalance -= requiredMargin;
        positions.put(symbol + ":LONG", tradeAmount);
        entryPrices.put(symbol + ":LONG", price);
        
        return OrderResult.builder()
            .exchangeOrderId("PAPER-" + UUID.randomUUID().toString())
            .symbol(symbol)
            .side("Buy")
            .status(OrderResult.OrderStatus.FILLED)
            .orderedQuantity(tradeAmount)
            .filledQuantity(tradeAmount)
            .avgFillPrice(price)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Override
    public OrderResult exitLongPosition(String symbol, double tradeAmount) {
        double price = getCurrentPrice(symbol);
        Double entryPrice = entryPrices.get(symbol + ":LONG");
        if (entryPrice == null) {
            throw new BotOperationException("exitLongPosition", "No long position found");
        }
        double profit = (price - entryPrice) * tradeAmount * leverage;
        marginBalance += profit + (tradeAmount * entryPrice / leverage);
        positions.remove(symbol + ":LONG");
        entryPrices.remove(symbol + ":LONG");
        
        return OrderResult.builder()
            .exchangeOrderId("PAPER-" + UUID.randomUUID().toString())
            .symbol(symbol)
            .side("Sell")
            .status(OrderResult.OrderStatus.FILLED)
            .orderedQuantity(tradeAmount)
            .filledQuantity(tradeAmount)
            .avgFillPrice(price)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Override
    public OrderResult enterShortPosition(String symbol, double tradeAmount) {
        double price = getCurrentPrice(symbol);
        double requiredMargin = tradeAmount * price / leverage;
        if (marginBalance < requiredMargin) throw new BotOperationException("enterShortPosition", "Insufficient margin");
        marginBalance -= requiredMargin;
        positions.put(symbol + ":SHORT", tradeAmount);
        entryPrices.put(symbol + ":SHORT", price);
        
        return OrderResult.builder()
            .exchangeOrderId("PAPER-" + UUID.randomUUID().toString())
            .symbol(symbol)
            .side("Sell")
            .status(OrderResult.OrderStatus.FILLED)
            .orderedQuantity(tradeAmount)
            .filledQuantity(tradeAmount)
            .avgFillPrice(price)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Override
    public OrderResult exitShortPosition(String symbol, double tradeAmount) {
        double price = getCurrentPrice(symbol);
        Double entryPrice = entryPrices.get(symbol + ":SHORT");
        if (entryPrice == null) {
            throw new BotOperationException("exitShortPosition", "No short position found");
        }
        double profit = (entryPrice - price) * tradeAmount * leverage;
        marginBalance += profit + (tradeAmount * entryPrice / leverage);
        positions.remove(symbol + ":SHORT");
        entryPrices.remove(symbol + ":SHORT");
        
        return OrderResult.builder()
            .exchangeOrderId("PAPER-" + UUID.randomUUID().toString())
            .symbol(symbol)
            .side("Buy")
            .status(OrderResult.OrderStatus.FILLED)
            .orderedQuantity(tradeAmount)
            .filledQuantity(tradeAmount)
            .avgFillPrice(price)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
    
    @Override
    public OrderResult placeStopLossOrder(String symbol, String side, double quantity, double stopPrice) {
        // Simulated - just return a placeholder order
        return OrderResult.builder()
            .exchangeOrderId("PAPER-SL-" + UUID.randomUUID().toString())
            .symbol(symbol)
            .side(side)
            .status(OrderResult.OrderStatus.NEW)
            .orderedQuantity(quantity)
            .filledQuantity(0.0)
            .avgFillPrice(stopPrice)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
    
    @Override
    public OrderResult placeTakeProfitOrder(String symbol, String side, double quantity, double takeProfitPrice) {
        // Simulated - just return a placeholder order
        return OrderResult.builder()
            .exchangeOrderId("PAPER-TP-" + UUID.randomUUID().toString())
            .symbol(symbol)
            .side(side)
            .status(OrderResult.OrderStatus.NEW)
            .orderedQuantity(quantity)
            .filledQuantity(0.0)
            .avgFillPrice(takeProfitPrice)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
}
