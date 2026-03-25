package tradingbot.agent.domain.model;

/**
 * TradeDirection - Enum representing the direction of a trade
 * 
 * Used for both TradingMemory (historical) and Order (future) trades.
 */
public enum TradeDirection {
    /**
     * Buy/Long - betting the price will go up
     */
    LONG,
    
    /**
     * Sell/Short - betting the price will go down
     */
    SHORT
}
