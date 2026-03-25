package tradingbot.bot.controller.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when bot is not initialized
 */
public class BotNotInitializedException extends TradingBotException {

    public BotNotInitializedException() {
        super("BOT_NOT_INITIALIZED", "Trading bot is not properly initialized", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}