package tradingbot.bot.controller.exception;

/**
 * Exception thrown when a resource conflict occurs.
 * For example, trying to start a bot that's already running.
 * Results in HTTP 409 Conflict response.
 */
public class ConflictException extends RuntimeException {
    
    public ConflictException(String message) {
        super(message);
    }
    
    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
