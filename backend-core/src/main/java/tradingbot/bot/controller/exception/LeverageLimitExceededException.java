package tradingbot.bot.controller.exception;

/**
 * Exception thrown when requested leverage exceeds exchange or system limits
 * 
 * Different exchanges have different maximum leverage limits:
 * - Binance: typically 1-125x depending on asset
 * - Paper trading: configurable limits
 */
public class LeverageLimitExceededException extends RuntimeException {
    
    private final int requestedLeverage;
    private final int maxLeverage;
    private final String symbol;
    
    public LeverageLimitExceededException(int requested, int max, String symbol) {
        super(String.format("Leverage %dx exceeds maximum allowed %dx for symbol %s", 
                          requested, max, symbol));
        this.requestedLeverage = requested;
        this.maxLeverage = max;
        this.symbol = symbol;
    }
    
    public LeverageLimitExceededException(int requested, int max) {
        super(String.format("Leverage %dx exceeds maximum allowed %dx", 
                          requested, max));
        this.requestedLeverage = requested;
        this.maxLeverage = max;
        this.symbol = null;
    }
    
    public int getRequestedLeverage() {
        return requestedLeverage;
    }
    
    public int getMaxLeverage() {
        return maxLeverage;
    }
    
    public String getSymbol() {
        return symbol;
    }
}
