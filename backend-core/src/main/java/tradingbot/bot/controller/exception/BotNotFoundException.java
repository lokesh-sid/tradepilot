package tradingbot.bot.controller.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a bot with the specified ID is not found
 */
@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Trading bot not found")
public class BotNotFoundException extends RuntimeException {
    
    public BotNotFoundException(String botId) {
        super("Trading bot not found with ID: " + botId);
    }
    
    public BotNotFoundException(String botId, Throwable cause) {
        super("Trading bot not found with ID: " + botId, cause);
    }
}
