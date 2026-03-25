package tradingbot.agent.api.controller;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import java.util.Arrays;

import tradingbot.bot.controller.dto.response.ErrorResponse;

/**
 * Handles enum deserialization errors for order API, returning a clear message with allowed values.
 */
@RestControllerAdvice(basePackages = "tradingbot.agent.api.controller")
public class OrderExceptionHandler {

    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFormat(InvalidFormatException ex, WebRequest request) {
        String fieldName = ex.getPathReference();
        String targetType = ex.getTargetType() != null ? ex.getTargetType().getSimpleName() : "unknown";
        String allowed = "";
        if (ex.getTargetType() != null && ex.getTargetType().isEnum()) {
            Object[] values = ex.getTargetType().getEnumConstants();
            allowed = " Allowed values: [" + String.join(", ", Arrays.stream(values).map(Object::toString).toList()) + "]";
        }
        String detail = "Invalid value for " + fieldName + ". Expected type: " + targetType + "." + allowed;
        ErrorResponse error = ErrorResponse.builder()
                .httpStatus(HttpStatus.BAD_REQUEST.value())
                .title("Invalid Enum Value")
                .detail(detail)
                .instance(request.getDescription(false).replace("uri=", ""))
                .timestamp(System.currentTimeMillis())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}