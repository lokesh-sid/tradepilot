package tradingbot.bot.controller.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for trading bot operations
 */
public abstract class TradingBotException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected TradingBotException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected TradingBotException(String errorCode, String message, Throwable cause, HttpStatus httpStatus) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}