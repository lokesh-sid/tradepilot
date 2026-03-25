package tradingbot.bot.controller.exception;

/**
 * Exception thrown when an invalid trading operation is attempted
 * 
 * Examples:
 * - Trying to start a bot that's already running
 * - Attempting to trade with insufficient balance
 * - Invalid order parameters
 */
public class InvalidTradingOperationException extends RuntimeException {
    
    private final String botId;
    private final String operation;
    
    public InvalidTradingOperationException(String botId, String operation, String reason) {
        super(String.format("Invalid operation '%s' for bot '%s': %s", 
                          operation, botId, reason));
        this.botId = botId;
        this.operation = operation;
    }
    
    public InvalidTradingOperationException(String message) {
        super(message);
        this.botId = null;
        this.operation = null;
    }
    
    public String getBotId() {
        return botId;
    }
    
    public String getOperation() {
        return operation;
    }
}
