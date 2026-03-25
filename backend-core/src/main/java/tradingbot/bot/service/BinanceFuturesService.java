package tradingbot.bot.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import tradingbot.bot.controller.exception.BotOperationException;
import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.messaging.EventPublisher;

public class BinanceFuturesService implements FuturesExchangeService {
    private UMFuturesClientImpl futuresClient;
    private ObjectMapper objectMapper;
    private final EventPublisher eventPublisher;
    
    // Snowflake Generator Instance for guaranteed global uniqueness
    private static final SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator(getMachineId());

    public BinanceFuturesService(String apiKey, String apiSecret, EventPublisher eventPublisher) {
        this.futuresClient = new UMFuturesClientImpl(apiKey, apiSecret);
        this.objectMapper = new ObjectMapper();
        this.eventPublisher = eventPublisher;
    }

    public BinanceFuturesService(String apiKey, String apiSecret) {
        this(apiKey, apiSecret, null);
    }
    
    private void publishTradeExecution(OrderResult result, String side, double quantity, double price) {
        try {
            if (this.eventPublisher != null) {
                TradeExecutionEvent event = new TradeExecutionEvent();
                event.setBotId("binance-bot"); // Default for now
                event.setOrderId(result.getExchangeOrderId());
                event.setSymbol(result.getSymbol());
                event.setSide(side);
                event.setQuantity(quantity);
                event.setPrice(price);
                event.setStatus(result.getStatus().toString());
                
                this.eventPublisher.publishTradeExecution(event);
            }
        } catch (Exception e) {
            // Log but don't fail the trade
            // logger.error("Failed to publish trade event", e); 
        }
    }
    
    /**
     * Generates a globally unique clientOrderId using Snowflake algorithm.
     * Format: PREFIX-SNOWFLAKE_ID (e.g., LONG-12874123491238)
     */
    private String generateClientOrderId(String prefix) {
        return prefix + "-" + idGenerator.nextId();
    }

    /**
     * Helper to determine price precision based on asset value.
     * Prevents API errors for assets like PEPE (8 decimals) or BTC (2 decimals).
     */
    private int getPricePrecision(double price) {
        if (price > 1000) return 2; // e.g. BTC
        if (price > 10) return 3;   // e.g. SOL, LINK
        if (price > 1) return 4;    // e.g. XRP, MATIC
        if (price > 0.01) return 6; // e.g. DOGE
        return 8;                   // e.g. SHIB, PEPE
    }


    @Override
    public List<Candle> fetchOhlcv(String symbol, String timeframe, int limit) {
        try {
            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("symbol", symbol);
            parameters.put("interval", timeframe);
            parameters.put("limit", limit);
            String result = futuresClient.market().klines(parameters);
            ArrayNode candlesArray = (ArrayNode) objectMapper.readTree(result);
            List<Candle> candles = new LinkedList<>();
            for (int i = 0; i < candlesArray.size(); i++) {
                ArrayNode candleData = (ArrayNode) candlesArray.get(i);
                Candle candle = new Candle();
                candle.setOpenTime(candleData.get(0).asLong());
                candle.setOpen(new BigDecimal(candleData.get(1).asText()));
                candle.setHigh(new BigDecimal(candleData.get(2).asText()));
                candle.setLow(new BigDecimal(candleData.get(3).asText()));
                candle.setClose(new BigDecimal(candleData.get(4).asText()));
                candle.setVolume(new BigDecimal(candleData.get(5).asText()));
                candle.setCloseTime(candleData.get(6).asLong());
                candles.add(candle);
            }
            return candles;
        } catch (Exception e) {
            throw new BotOperationException("fetch_ohlcv", "Failed to fetch OHLCV data for " + symbol, e);
        }
    }

    @Override
    public double getCurrentPrice(String symbol) {
        try {
            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("symbol", symbol);
            String result = futuresClient.market().markPrice(parameters);
            ObjectNode markPrice = (ObjectNode) objectMapper.readTree(result);
            return markPrice.get("markPrice").asDouble();
        } catch (Exception e) {
            throw new BotOperationException("fetch_price", "Failed to fetch current price for " + symbol, e);
        }
    }

    @Override
    public double getMarginBalance() {
        try {
            String result = futuresClient.account().accountInformation(null);
            ObjectNode accountInfo = (ObjectNode) objectMapper.readTree(result);
            ArrayNode assets = (ArrayNode) accountInfo.get("assets");
            for (int i = 0; i < assets.size(); i++) {
                ObjectNode asset = (ObjectNode) assets.get(i);
                if ("USDT".equals(asset.get("asset").asText())) {
                    return asset.get("availableBalance").asDouble();
                }
            }
            return 0.0;
        } catch (Exception e) {
            throw new BotOperationException("fetch_balance", "Failed to fetch margin balance", e);
        }
    }
    
    @Override
    public Ticker24hrStats get24HourStats(String symbol) {
        try {
            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("symbol", symbol);
            String result = futuresClient.market().ticker24H(parameters);
            ObjectNode ticker = (ObjectNode) objectMapper.readTree(result);
            
            return Ticker24hrStats.builder()
                .symbol(symbol)
                .volume(ticker.get("volume").asDouble())
                .quoteVolume(ticker.get("quoteVolume").asDouble())
                .priceChange(ticker.get("priceChange").asDouble())
                .priceChangePercent(ticker.get("priceChangePercent").asDouble())
                .highPrice(ticker.get("highPrice").asDouble())
                .lowPrice(ticker.get("lowPrice").asDouble())
                .openPrice(ticker.get("openPrice").asDouble())
                .lastPrice(ticker.get("lastPrice").asDouble())
                .build();
        } catch (Exception e) {
            throw new BotOperationException("fetch_24h_stats", "Failed to fetch 24h stats for " + symbol, e);
        }
    }

    @Override
    public void setLeverage(String symbol, int leverage) {
        try {
            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("symbol", symbol);
            parameters.put("leverage", leverage);
            futuresClient.account().changeInitialLeverage(parameters);
        } catch (Exception e) {
            throw new BotOperationException("set_leverage", "Failed to set leverage for " + symbol, e);
        }
    }

    @Override
    public OrderResult enterLongPosition(String symbol, double tradeAmount) {
        try {
            // 1. Get current price & determine precision
            double currentPrice = getCurrentPrice(symbol);
            int precision = getPricePrecision(currentPrice);

            // Buy slightly above market (0.2%) to ensure fill but prevent massive slippage
            BigDecimal entryPrice = BigDecimal.valueOf(currentPrice).multiply(BigDecimal.valueOf(1.002));
            
            // 2. Generate Idempotency Key
            String clientOrderId = generateClientOrderId("LONG");

            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("symbol", symbol);
            parameters.put("side", "BUY");
            parameters.put("positionSide", "LONG");
            parameters.put("type", "LIMIT");
            // Use 'IOC' (Immediate or Cancel) ensures we don't get stuck with an open order if price moves away
            parameters.put("timeInForce", "IOC");
            parameters.put("quantity", String.valueOf(tradeAmount));
            parameters.put("price", entryPrice.setScale(precision, RoundingMode.HALF_UP).toString()); 
            parameters.put("newClientOrderId", clientOrderId);
            
            String response = futuresClient.account().newOrder(parameters);
            
            // Validate that IOC order was actually filled
            ObjectNode jsonResponse = (ObjectNode) objectMapper.readTree(response);
            validateOrderFill(jsonResponse);
            
            // Extract order details
            String orderId = jsonResponse.get("orderId").asText();
            String status = jsonResponse.get("status").asText();
            double executedQty = jsonResponse.has("executedQty") ? jsonResponse.get("executedQty").asDouble() : tradeAmount;
            double avgPrice = jsonResponse.has("avgPrice") ? jsonResponse.get("avgPrice").asDouble() : currentPrice;
            
            OrderResult result = OrderResult.builder()
                .exchangeOrderId(orderId)
                .clientOrderId(clientOrderId)
                .symbol(symbol)
                .side("BUY")
                .status(mapStatus(status))
                .orderedQuantity(tradeAmount)
                .filledQuantity(executedQty)
                .avgFillPrice(avgPrice)
                .commission(0.0) // Would need separate API call
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
            
            publishTradeExecution(result, "BUY", executedQty, avgPrice);
            return result;

        } catch (Exception e) {
            throw new BotOperationException("enter_long_position", "Failed to enter long position for " + symbol, e);
        }
    }

    @Override
    public OrderResult enterShortPosition(String symbol, double tradeAmount) {
        try {
             // 1. Get current price & determine precision
            double currentPrice = getCurrentPrice(symbol);
            int precision = getPricePrecision(currentPrice);

            // Sell slightly below market (0.2%) to ensure fill
            BigDecimal entryPrice = BigDecimal.valueOf(currentPrice).multiply(BigDecimal.valueOf(0.998));
            
            // 2. Generate Idempotency Key
            String clientOrderId = generateClientOrderId("SHORT");
            
            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("symbol", symbol);
            parameters.put("side", "SELL");
            parameters.put("positionSide", "SHORT");
            parameters.put("type", "LIMIT");
            // Use 'IOC' (Immediate or Cancel) ensures we don't get stuck with an open order if price moves away
            parameters.put("timeInForce", "IOC");
            parameters.put("quantity", String.valueOf(tradeAmount));
            parameters.put("price", entryPrice.setScale(precision, RoundingMode.HALF_UP).toString());
            parameters.put("newClientOrderId", clientOrderId);
            
            String response = futuresClient.account().newOrder(parameters);
            
             // Validate that IOC order was actually filled
            ObjectNode jsonResponse = (ObjectNode) objectMapper.readTree(response);
            validateOrderFill(jsonResponse);
            
            // Extract order details
            String orderId = jsonResponse.get("orderId").asText();
            String status = jsonResponse.get("status").asText();
            double executedQty = jsonResponse.has("executedQty") ? jsonResponse.get("executedQty").asDouble() : tradeAmount;
            double avgPrice = jsonResponse.has("avgPrice") ? jsonResponse.get("avgPrice").asDouble() : currentPrice;
            
            OrderResult result = OrderResult.builder()
                .exchangeOrderId(orderId)
                .clientOrderId(clientOrderId)
                .symbol(symbol)
                .side("SELL")
                .status(mapStatus(status))
                .orderedQuantity(tradeAmount)
                .filledQuantity(executedQty)
                .avgFillPrice(avgPrice)
                .commission(0.0) // Would need separate API call
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
            
            publishTradeExecution(result, "SELL", executedQty, avgPrice);
            return result;
            
        } catch (Exception e) {
            throw new BotOperationException("enter_short_position", "Failed to enter short position for " + symbol, e);
        }
    }

    private void validateOrderFill(ObjectNode response) throws BotOperationException {
        String status = response.get("status").asText();
        if ("EXPIRED".equals(status) || "CANCELED".equals(status)) {
            throw new BotOperationException("order_execution", "Limit Order IOC expired/canceled before fill. Slippage exceeded tolerance.");
        }
    }

    @Override
    public OrderResult exitLongPosition(String symbol, double tradeAmount) {
        try {
            String clientOrderId = generateClientOrderId("EXIT-LONG");
            double currentPrice = getCurrentPrice(symbol);
            
            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("symbol", symbol);
            parameters.put("side", "SELL");
            parameters.put("positionSide", "LONG");
            parameters.put("type", "MARKET");
            parameters.put("quantity", tradeAmount);
            parameters.put("newClientOrderId", clientOrderId);
            
            String response = futuresClient.account().newOrder(parameters);
            ObjectNode jsonResponse = (ObjectNode) objectMapper.readTree(response);
            
            String orderId = jsonResponse.get("orderId").asText();
            String status = jsonResponse.get("status").asText();
            double executedQty = jsonResponse.has("executedQty") ? jsonResponse.get("executedQty").asDouble() : tradeAmount;
            double avgPrice = jsonResponse.has("avgPrice") ? jsonResponse.get("avgPrice").asDouble() : currentPrice;
            
            OrderResult result = OrderResult.builder()
                .exchangeOrderId(orderId)
                .clientOrderId(clientOrderId)
                .symbol(symbol)
                .side("SELL")
                .status(mapStatus(status))
                .orderedQuantity(tradeAmount)
                .filledQuantity(executedQty)
                .avgFillPrice(avgPrice)
                .commission(0.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
            
            publishTradeExecution(result, "SELL", executedQty, avgPrice);
            return result;
                
        } catch (Exception e) {
            throw new BotOperationException("exit_long_position", "Failed to exit long position for " + symbol, e);
        }
    }

    @Override
    public OrderResult exitShortPosition(String symbol, double tradeAmount) {
        try {
            String clientOrderId = generateClientOrderId("EXIT-SHORT");
            double currentPrice = getCurrentPrice(symbol);
            
            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("symbol", symbol);
            parameters.put("side", "BUY");
            parameters.put("positionSide", "SHORT");
            parameters.put("type", "MARKET");
            parameters.put("quantity", tradeAmount);
            parameters.put("newClientOrderId", clientOrderId);
            
            String response = futuresClient.account().newOrder(parameters);
            ObjectNode jsonResponse = (ObjectNode) objectMapper.readTree(response);
            
            String orderId = jsonResponse.get("orderId").asText();
            String status = jsonResponse.get("status").asText();
            double executedQty = jsonResponse.has("executedQty") ? jsonResponse.get("executedQty").asDouble() : tradeAmount;
            double avgPrice = jsonResponse.has("avgPrice") ? jsonResponse.get("avgPrice").asDouble() : currentPrice;
            
            OrderResult result = OrderResult.builder()
                .exchangeOrderId(orderId)
                .clientOrderId(clientOrderId)
                .symbol(symbol)
                .side("BUY")
                .status(mapStatus(status))
                .orderedQuantity(tradeAmount)
                .filledQuantity(executedQty)
                .avgFillPrice(avgPrice)
                .commission(0.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
            
            publishTradeExecution(result, "BUY", executedQty, avgPrice);
            return result;
                
        } catch (Exception e) {
            throw new BotOperationException("exit_short_position", "Failed to exit short position for " + symbol, e);
        }
    }
    
    @Override
    public OrderResult placeStopLossOrder(String symbol, String side, double quantity, double stopPrice) {
        try {
            String clientOrderId = generateClientOrderId("SL");
            double currentPrice = getCurrentPrice(symbol);
            int precision = getPricePrecision(stopPrice);
            
            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("symbol", symbol);
            parameters.put("side", side);
            parameters.put("positionSide", side.equals("BUY") ? "SHORT" : "LONG");
            parameters.put("type", "STOP_MARKET");
            parameters.put("stopPrice", BigDecimal.valueOf(stopPrice).setScale(precision, RoundingMode.HALF_UP).toString());
            parameters.put("quantity", String.valueOf(quantity));
            parameters.put("closePosition", "true");
            parameters.put("workingType", "MARK_PRICE");
            parameters.put("newClientOrderId", clientOrderId);
            
            String response = futuresClient.account().newOrder(parameters);
            ObjectNode jsonResponse = (ObjectNode) objectMapper.readTree(response);
            
            String orderId = jsonResponse.get("orderId").asText();
            
            return OrderResult.builder()
                .exchangeOrderId(orderId)
                .clientOrderId(clientOrderId)
                .symbol(symbol)
                .side(side)
                .status(OrderResult.OrderStatus.NEW)
                .orderedQuantity(quantity)
                .filledQuantity(0.0)
                .avgFillPrice(0.0)
                .commission(0.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
                
        } catch (Exception e) {
            throw new BotOperationException("place_stop_loss", "Failed to place stop loss order for " + symbol, e);
        }
    }
    
    @Override
    public OrderResult placeTakeProfitOrder(String symbol, String side, double quantity, double takeProfitPrice) {
        try {
            String clientOrderId = generateClientOrderId("TP");
            double currentPrice = getCurrentPrice(symbol);
            int precision = getPricePrecision(takeProfitPrice);
            
            LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("symbol", symbol);
            parameters.put("side", side);
            parameters.put("positionSide", side.equals("BUY") ? "SHORT" : "LONG");
            parameters.put("type", "TAKE_PROFIT_MARKET");
            parameters.put("stopPrice", BigDecimal.valueOf(takeProfitPrice).setScale(precision, RoundingMode.HALF_UP).toString());
            parameters.put("quantity", String.valueOf(quantity));
            parameters.put("closePosition", "true");
            parameters.put("workingType", "MARK_PRICE");
            parameters.put("newClientOrderId", clientOrderId);
            
            String response = futuresClient.account().newOrder(parameters);
            ObjectNode jsonResponse = (ObjectNode) objectMapper.readTree(response);
            
            String orderId = jsonResponse.get("orderId").asText();
            
            return OrderResult.builder()
                .exchangeOrderId(orderId)
                .clientOrderId(clientOrderId)
                .symbol(symbol)
                .side(side)
                .status(OrderResult.OrderStatus.NEW)
                .orderedQuantity(quantity)
                .filledQuantity(0.0)
                .avgFillPrice(0.0)
                .commission(0.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
                
        } catch (Exception e) {
            throw new BotOperationException("place_take_profit", "Failed to place take profit order for " + symbol, e);
        }
    }
    
    private OrderResult.OrderStatus mapStatus(String binanceStatus) {
        return switch (binanceStatus) {
            case "NEW" -> OrderResult.OrderStatus.NEW;
            case "PARTIALLY_FILLED" -> OrderResult.OrderStatus.PARTIALLY_FILLED;
            case "FILLED" -> OrderResult.OrderStatus.FILLED;
            case "CANCELED" -> OrderResult.OrderStatus.CANCELED;
            case "REJECTED" -> OrderResult.OrderStatus.REJECTED;
            case "EXPIRED" -> OrderResult.OrderStatus.EXPIRED;
            default -> OrderResult.OrderStatus.NEW;
        };
    }

    // --- Snowflake Implementation ---

    /** 
     * Generates a unique Machine ID based on network address 
     * to prevent collisions in distributed deployments 
     */
    private static long getMachineId() {
        try {
            StringBuilder sb = new StringBuilder();
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                byte[] mac = networkInterface.getHardwareAddress();
                if (mac != null) {
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b));
                    }
                }
            }
            return (sb.toString().hashCode() & 0xFFFF) % 31; // Mask to fit 5 bits (0-31)
        } catch (Exception ex) {
            return new SecureRandom().nextInt(31);
        }
    }
    
    // Simple Embedded Snowflake Generator (Twitter Standard)
    private static class SnowflakeIdGenerator {
        private final long workerId;
        private final long epoch = 1704067200000L; // Custom Epoch (Jan 1, 2024)
        private long sequence = 0L;
        private long lastTimestamp = -1L;

        private final long workerIdBits = 5L;
        private final long sequenceBits = 12L;
        private final long workerIdShift = sequenceBits;
        private final long timestampLeftShift = sequenceBits + workerIdBits;
        private final long sequenceMask = ~(-1L << sequenceBits);

        public SnowflakeIdGenerator(long workerId) {
            if (workerId > 31 || workerId < 0) {
                throw new IllegalArgumentException("worker Id can't be greater than 31 or less than 0");
            }
            this.workerId = workerId;
        }

        public synchronized long nextId() {
            long timestamp = System.currentTimeMillis();
            if (timestamp < lastTimestamp) {
                throw new RuntimeException("Clock moved backwards. Refusing to generate id");
            }
            if (lastTimestamp == timestamp) {
                sequence = (sequence + 1) & sequenceMask;
                if (sequence == 0) {
                    while ((timestamp = System.currentTimeMillis()) <= lastTimestamp);
                }
            } else {
                sequence = 0L;
            }
            lastTimestamp = timestamp;
            return ((timestamp - epoch) << timestampLeftShift) | (workerId << workerIdShift) | sequence;
        }
    }

    public static class Candle {
        private long openTime;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal volume;
        private long closeTime;

        public long getOpenTime() { return openTime; }
        public void setOpenTime(long openTime) { this.openTime = openTime; }
        public BigDecimal getOpen() { return open; }
        public void setOpen(BigDecimal open) { this.open = open; }
        public BigDecimal getHigh() { return high; }
        public void setHigh(BigDecimal high) { this.high = high; }
        public BigDecimal getLow() { return low; }
        public void setLow(BigDecimal low) { this.low = low; }
        public BigDecimal getClose() { return close; }
        public void setClose(BigDecimal close) { this.close = close; }
        public BigDecimal getVolume() { return volume; }
        public void setVolume(BigDecimal volume) { this.volume = volume; }
        public long getCloseTime() { return closeTime; }
        public void setCloseTime(long closeTime) { this.closeTime = closeTime; }
    }
}