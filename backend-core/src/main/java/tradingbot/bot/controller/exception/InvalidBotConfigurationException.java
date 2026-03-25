package tradingbot.bot.controller.exception;

/**
 * Exception thrown when bot configuration is invalid
 */
public class InvalidBotConfigurationException extends RuntimeException {
    
    public InvalidBotConfigurationException(String message) {
        super(message);
    }
    
    public InvalidBotConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
