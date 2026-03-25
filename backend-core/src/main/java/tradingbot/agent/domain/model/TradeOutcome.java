package tradingbot.agent.domain.model;

/**
 * TradeOutcome - The final result of a trade
 */
public enum TradeOutcome {
    /**
     * Trade resulted in profit
     */
    PROFIT,
    
    /**
     * Trade resulted in loss
     */
    LOSS,
    
    /**
     * Trade broke even (no profit, no loss)
     */
    BREAKEVEN,
    
    /**
     * Trade is still open/pending
     */
    PENDING,
    
    /**
     * Trade was cancelled before execution
     */
    CANCELLED
}
