package tradingbot.bot.service;

import java.util.List;

import tradingbot.bot.service.BinanceFuturesService.Candle;

public interface FuturesExchangeService {
    List<Candle> fetchOhlcv(String symbol, String timeframe, int limit);
    double getCurrentPrice(String symbol);
    double getMarginBalance();
    
    /**
     * Get 24-hour trading statistics for a symbol
     * @param symbol Trading symbol (e.g., BTCUSDT)
     * @return 24-hour statistics including volume and price change
     */
    Ticker24hrStats get24HourStats(String symbol);
    
    void setLeverage(String symbol, int leverage);
    
    /**
     * Enter long position (buy) - NEW: Returns OrderResult with real exchange order ID
     * @param symbol Trading symbol
     * @param tradeAmount Quantity to buy
     * @return OrderResult with exchange order details
     */
    OrderResult enterLongPosition(String symbol, double tradeAmount);
    
    /**
     * Exit long position (sell) - NEW: Returns OrderResult with real exchange order ID
     * @param symbol Trading symbol
     * @param tradeAmount Quantity to sell
     * @return OrderResult with exchange order details
     */
    OrderResult exitLongPosition(String symbol, double tradeAmount);
    
    /**
     * Enter short position (sell) - NEW: Returns OrderResult with real exchange order ID
     * @param symbol Trading symbol
     * @param tradeAmount Quantity to sell short
     * @return OrderResult with exchange order details
     */
    OrderResult enterShortPosition(String symbol, double tradeAmount);
    
    /**
     * Exit short position (buy to cover) - NEW: Returns OrderResult with real exchange order ID
     * @param symbol Trading symbol
     * @param tradeAmount Quantity to buy to cover
     * @return OrderResult with exchange order details
     */
    OrderResult exitShortPosition(String symbol, double tradeAmount);
    
    /**
     * Place a stop-loss order
     * @param symbol Trading symbol
     * @param side Buy or Sell
     * @param quantity Order quantity
     * @param stopPrice Stop trigger price
     * @return OrderResult with exchange order details
     */
    OrderResult placeStopLossOrder(String symbol, String side, double quantity, double stopPrice);
    
    /**
     * Place a take-profit order
     * @param symbol Trading symbol
     * @param side Buy or Sell
     * @param quantity Order quantity
     * @param takeProfitPrice Take profit trigger price
     * @return OrderResult with exchange order details
     */
    OrderResult placeTakeProfitOrder(String symbol, String side, double quantity, double takeProfitPrice);
}