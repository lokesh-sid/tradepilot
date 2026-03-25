package tradingbot.bot.controller.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when bot operation fails
 */
public class BotOperationException extends TradingBotException {

    public BotOperationException(String operation, String message) {
        super("BOT_" + operation.toUpperCase() + "_FAILED", "Failed to " + operation + ": " + message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public BotOperationException(String operation, String message, Throwable cause) {
        super("BOT_" + operation.toUpperCase() + "_FAILED", "Failed to " + operation + ": " + message, cause, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}