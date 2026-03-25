package tradingbot.security.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tradingbot.security.dto.HealthResponse;
import tradingbot.security.dto.LoginRequest;
import tradingbot.security.dto.LoginResponse;
import tradingbot.security.dto.LogoutRequest;
import tradingbot.security.dto.LogoutResponse;
import tradingbot.security.dto.RefreshTokenRequest;
import tradingbot.security.dto.RegisterRequest;
import tradingbot.security.service.AuthService;

/**
 * Authentication Controller
 * 
 * Handles user registration, login, token refresh, and logout operations.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    private final AuthService authService;
    
    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    
    /**
     * Register a new user
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            logger.info("Registration request for username: {}", request.username());
            LoginResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(LoginResponse.error(
                        "invalid_request",  // RFC 6749 error code
                        e.getMessage()
                    ));
        } catch (Exception e) {
            logger.error("Registration error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(LoginResponse.error(
                        "server_error",  // RFC 6749 error code
                        "Registration failed. Please try again later."
                    ));
        }
    }
    
    /**
     * Login user
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            logger.info("Login request for username: {}", request.username());
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(LoginResponse.error(
                        "invalid_grant",  // RFC 6749 error code for authentication failures
                        e.getMessage()
                    ));
        } catch (Exception e) {
            logger.error("Login error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(LoginResponse.error(
                        "server_error",  // RFC 6749 error code
                        "Login failed. Please try again later."
                    ));
        }
    }
    
    /**
     * Refresh access token
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            logger.info("Token refresh request");
            LoginResponse response = authService.refreshToken(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(LoginResponse.error(
                        "invalid_token",  // RFC 6749 error code
                        e.getMessage()
                    ));
        } catch (Exception e) {
            logger.error("Token refresh error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(LoginResponse.error(
                        "server_error",  // RFC 6749 error code
                        "Token refresh failed. Please try again later."
                    ));
        }
    }
    
    /**
     * Logout — revokes the supplied refresh-token family server-side.
     * POST /api/auth/logout
     *
     * The client should also delete its local tokens.
     * The request body is optional for backward compatibility; if omitted,
     * the call still succeeds but no token is revoked server-side.
     */
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @RequestBody(required = false) LogoutRequest request) {
        String token = (request != null) ? request.refreshToken() : null;
        authService.logout(token);
        logger.info("Logout processed");
        return ResponseEntity.ok(LogoutResponse.success());
    }
    
    /**
     * Health check endpoint
     * GET /api/auth/health
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.up());
    }
}
