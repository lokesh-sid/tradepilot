package tradingbot.bot.controller.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when bot is already running
 */
public class BotAlreadyRunningException extends TradingBotException {

    public BotAlreadyRunningException() {
        super("BOT_ALREADY_RUNNING", "Trading bot is already running. Stop it before starting a new instance.", HttpStatus.CONFLICT);
    }
}