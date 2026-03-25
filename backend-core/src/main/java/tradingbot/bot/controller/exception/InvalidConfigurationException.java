package tradingbot.bot.controller.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown for invalid configuration
 */
public class InvalidConfigurationException extends TradingBotException {

    public InvalidConfigurationException(String message) {
        super("INVALID_CONFIGURATION", "Invalid configuration: " + message, HttpStatus.BAD_REQUEST);
    }
}