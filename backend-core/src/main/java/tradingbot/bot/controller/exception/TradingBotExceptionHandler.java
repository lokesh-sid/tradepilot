package tradingbot.bot.controller.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tradingbot.bot.controller.dto.response.ErrorResponse;

/**
 * Global exception handler for trading bot controller
 */
@ControllerAdvice
public class TradingBotExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(TradingBotException.class)
    public ResponseEntity<ErrorResponse> handleTradingBotException(TradingBotException ex) {
        ErrorResponse errorResponse = new ErrorResponse(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        ErrorResponse errorResponse = new ErrorResponse("UNEXPECTED_ERROR", "Unexpected error occurred: " + ex.getMessage());
        return ResponseEntity.status(500).body(errorResponse);
    }
}