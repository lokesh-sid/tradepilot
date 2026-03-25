package tradingbot.bot.controller.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when bot is not configured
 */
public class BotNotConfiguredException extends TradingBotException {

    public BotNotConfiguredException() {
        super("BOT_NOT_CONFIGURED", "Trading bot not configured", HttpStatus.NOT_FOUND);
    }
}