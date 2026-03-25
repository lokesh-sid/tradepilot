package tradingbot.security.service;

import java.time.Instant;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tradingbot.security.dto.LoginRequest;
import tradingbot.security.dto.LoginResponse;
import tradingbot.security.dto.RefreshTokenRequest;
import tradingbot.security.dto.RegisterRequest;
import tradingbot.security.entity.User;
import tradingbot.security.repository.UserRepository;

/**
 * Authentication Service
 * 
 * Handles user registration, login, and token refresh operations.
 */
@Service
public class AuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.authenticationManager = authenticationManager;
    }
    
    /**
     * Register a new user
     */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        logger.info("Registering new user: {}", request.username());
        
        // Validate username uniqueness
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already exists");
        }
        
        // Validate email uniqueness
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists");
        }
        
        // Create new user entity
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.getRoles().add("ROLE_USER"); // Default role
        user.setEnabled(true);
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        
        // Set default limits for free tier
        user.setMaxBots(10);
        user.setMaxApiCallsPerHour(1000);
        user.setMaxLeverage(100);
        user.setAccountTier("FREE");
        
        userRepository.save(user);
        
        logger.info("User registered successfully: {}", user.getUsername());
        
        // Generate tokens
        return generateTokenResponse(user);
    }
    
    /**
     * Login user with username and password
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        logger.info("User login attempt: {}", request.username());
        
        try {
            // Authenticate user
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
            
            // Get user from database
            User user = userRepository.findByUsername(request.username())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            // Update last login time
            user.setLastLogin(Instant.now());
            user.resetFailedLoginAttempts();
            userRepository.save(user);
            
            logger.info("User logged in successfully: {}", user.getUsername());
            
            // Generate tokens
            return generateTokenResponse(user);
            
        } catch (Exception e) {
            logger.error("Login failed for user {}: {}", request.username(), e.getMessage());
            
            // Increment failed login attempts
            userRepository.findByUsername(request.username()).ifPresent(user -> {
                user.incrementFailedLoginAttempts();
                
                // Lock account after 5 failed attempts
                if (user.getFailedLoginAttempts() >= 5) {
                    user.setAccountLocked(true);
                    logger.warn("User account locked due to failed login attempts: {}", user.getUsername());
                }
                
                userRepository.save(user);
            });
            
            throw new IllegalArgumentException("Invalid username or password");
        }
    }
    
    /**
     * Refresh access token using a valid, un-rotated refresh token.
     * Performs JWT validation first, then validates against the DB store
     * and rotates (old token marked used, new token issued in same family).
     */
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String incomingToken = request.refreshToken();

        // 1. Validate JWT structure and type before touching the DB
        if (!jwtService.isTokenValid(incomingToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }
        if (!jwtService.isRefreshToken(incomingToken)) {
            throw new IllegalArgumentException("Invalid token type");
        }

        // 2. Validate against DB: detect reuse, rotate
        String newRefreshToken = refreshTokenService.validateAndRotate(incomingToken);

        // 3. Resolve user for access-token claims
        String userId = jwtService.extractUserId(incomingToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isEnabled() || user.isAccountLocked()) {
            refreshTokenService.revokeAllForUser(userId);
            throw new IllegalArgumentException("User account is disabled or locked");
        }

        logger.info("Token refreshed for user: {}", user.getUsername());

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRoles());
        long expiresIn = jwtService.getAccessTokenExpirationSeconds();
        String scope = buildScope(user.getRoles());

        return new LoginResponse(accessToken, newRefreshToken, expiresIn, user.getId(), user.getUsername(), scope);
    }

    /**
     * Revokes the refresh-token family identified by {@code rawRefreshToken}.
     * Safe to call with an invalid or expired token (treated as no-op).
     */
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revokeByToken(rawRefreshToken);
        }
    }
    
    /**
     * Generates a full token response: new access token + persisted refresh token.
     */
    private LoginResponse generateTokenResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRoles());
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());
        long expiresIn = jwtService.getAccessTokenExpirationSeconds();
        String scope = buildScope(user.getRoles());
        return new LoginResponse(accessToken, refreshToken, expiresIn, user.getId(), user.getUsername(), scope);
    }

    private String buildScope(Set<String> roles) {
        return roles.stream()
                .map(r -> r.replace("ROLE_", "").toLowerCase())
                .sorted()
                .reduce((a, b) -> a + " " + b)
                .orElse("user");
    }
}
