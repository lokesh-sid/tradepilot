package tradingbot.bot.controller.exception;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import jakarta.validation.ConstraintViolationException;
import tradingbot.agent.application.AgentNotFoundException;
import tradingbot.bot.controller.dto.response.ErrorResponse;

/**
 * Global exception handler — maps all domain and validation exceptions
 * to consistent HTTP responses.
 *
 * Mapping contract:
 *  BotNotFoundException            → 404
 *  IllegalArgumentException        → 400  (wrong type / bad input)
 *  ConstraintViolationException    → 400  (Jakarta @Min, @Max, @Pattern)
 *  MethodArgumentNotValidException → 400  (@Valid on @RequestBody)
 *  IllegalStateException           → 409  (state conflict)
 *  ConflictException               → 409  (explicit conflict)
 *  UnsupportedOperationException   → 422  (capability not supported)
 *  Exception (catch-all)           → 500  (unexpected)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ------------------------------------------------------------------
    // 404 — Resource not found
    // ------------------------------------------------------------------

    @ExceptionHandler(BotNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBotNotFound(
            BotNotFoundException ex, WebRequest request) {
        ErrorResponse error = buildWithTitle(HttpStatus.NOT_FOUND, "Bot Not Found", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(AgentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAgentNotFound(
            AgentNotFoundException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // ------------------------------------------------------------------
    // 400 — Bad request (input validation)
    // ------------------------------------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        logger.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Malformed JSON request body — e.g. unrecognised enum value.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, WebRequest request) {
        logger.warn("Malformed request body: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body.", request);
    }

    /**
     * Jakarta @Min, @Max, @Pattern on @RequestParam / @PathVariable
     * Triggered because the class is annotated with @Validated.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        logger.warn("Constraint violation: {}", detail);
        ErrorResponse error = buildWithTitle(HttpStatus.BAD_REQUEST, "Constraint Violation", detail, request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Jakarta @Valid on @RequestBody
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, WebRequest request) {
        // Collect field-level errors into a map
        Map<String, List<String>> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
            fieldErrors.computeIfAbsent(e.getField(), k -> new ArrayList<>())
                       .add(e.getDefaultMessage())
        );
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        logger.warn("Validation failed: {}", detail);
        ErrorResponse error = buildWithTitle(HttpStatus.BAD_REQUEST, "Validation Failed", detail, request);
        error.setFieldErrors(fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ------------------------------------------------------------------
    // 409 — Conflict (state transition not allowed)
    // ------------------------------------------------------------------

    /**
     * Thrown by BotOperationPolicy when the bot is in the wrong runtime state.
     * e.g. trying to start an already running bot.
     */
    /**
     * Thrown when a start request is issued for a bot that is already running.
     */
    @ExceptionHandler(BotAlreadyRunningException.class)
    public ResponseEntity<ErrorResponse> handleBotAlreadyRunning(
            BotAlreadyRunningException ex, WebRequest request) {
        logger.warn("Bot already running: {}", ex.getMessage());
        ErrorResponse error = buildWithTitle(HttpStatus.CONFLICT, "Bot Already Running", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, WebRequest request) {
        logger.warn("State conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException ex, WebRequest request) {
        logger.warn("Conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // ------------------------------------------------------------------
    // 422 — Unprocessable (capability not supported by this bot type)
    // ------------------------------------------------------------------

    /**
     * Thrown by BotRequestValidator.resolveAgentAs() when the bot
     * does not implement the requested capability interface.
     * e.g. requesting leverage update on a spot bot.
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedOperation(
            UnsupportedOperationException ex, WebRequest request) {
        logger.warn("Unsupported operation: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    // ------------------------------------------------------------------
    // 500 — Unexpected
    // ------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, WebRequest request) {
        logger.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support.", request);
    }

    // ------------------------------------------------------------------
    // Builders
    // ------------------------------------------------------------------

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, WebRequest request) {
        return ResponseEntity.status(status).body(
                buildWithTitle(status, status.getReasonPhrase(), message, request));
    }

    private ErrorResponse buildWithTitle(
            HttpStatus status, String title, String detail, WebRequest request) {
        ErrorResponse error = new ErrorResponse();
        error.setHttpStatus(status.value());
        error.setTitle(title);
        error.setDetail(detail);
        error.setInstance(request.getDescription(false).replace("uri=", ""));
        error.setTimestamp(System.currentTimeMillis());
        return error;
    }
}
