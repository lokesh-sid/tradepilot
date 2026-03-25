package tradingbot.bot.controller.exception;

/**
 * Exception thrown when a bot operation is attempted in an invalid state
 * 
 * Examples:
 * - Trying to modify configuration while bot is running
 * - Attempting to stop a bot that's not running
 * - Trying to place order when bot is in error state
 */
public class InvalidBotStateException extends RuntimeException {
    
    private final String botId;
    private final String currentState;
    private final String requiredState;
    
    public InvalidBotStateException(String botId, String currentState, String requiredState) {
        super(String.format("Bot '%s' is in '%s' state, but '%s' state is required for this operation", 
                          botId, currentState, requiredState));
        this.botId = botId;
        this.currentState = currentState;
        this.requiredState = requiredState;
    }
    
    public InvalidBotStateException(String message) {
        super(message);
        this.botId = null;
        this.currentState = null;
        this.requiredState = null;
    }
    
    public String getBotId() {
        return botId;
    }
    
    public String getCurrentState() {
        return currentState;
    }
    
    public String getRequiredState() {
        return requiredState;
    }
}
