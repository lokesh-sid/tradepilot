package tradingbot.security.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;

/**
 * JWT Token Service
 * 
 * Handles JWT token generation, validation, and extraction.
 * Uses HMAC-SHA256 for signing tokens.
 */
@Service
public class JwtService {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);
    
    private static final int MIN_SECRET_LENGTH = 32;
    private static final Set<String> KNOWN_WEAK_SECRETS = Set.of(
            "dummy",
            "YourVerySecureSecretKeyThatIsAtLeast256BitsLong1234567890CHANGE_THIS_IN_PRODUCTION"
    );

    @Value("${jwt.secret}")
    private String jwtSecret;

    @PostConstruct
    void validateSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret must be set. Provide the JWT_SECRET environment variable.");
        }
        if (KNOWN_WEAK_SECRETS.contains(jwtSecret)) {
            throw new IllegalStateException(
                    "jwt.secret is a known placeholder value. "
                    + "Set a unique secret via the JWT_SECRET environment variable.");
        }
        if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "jwt.secret is too short (" + jwtSecret.length() + " chars). "
                    + "Minimum length is " + MIN_SECRET_LENGTH + " characters (256 bits).");
        }
        logger.info("JwtService initialised — secret length={} chars", jwtSecret.length());
    }
    
    @Value("${jwt.access-token-expiration:3600000}") // 1 hour default
    private long accessTokenExpiration;
    
    @Value("${jwt.refresh-token-expiration:86400000}") // 24 hours default
    private long refreshTokenExpiration;
    
    @Value("${jwt.issuer:tradepilot}")
    private String issuer;
    
    /**
     * Get signing key from secret
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    /**
     * Generate access token with user claims
     */
    public String generateAccessToken(String userId, String username, Set<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);
        
        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .claim("roles", new ArrayList<>(roles))
                .claim("type", "access")
                .subject(userId)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    /**
     * Generate refresh token
     */
    public String generateRefreshToken(String userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);
        
        return Jwts.builder()
                .claim("type", "refresh")
                .id(UUID.randomUUID().toString())  // jti: guarantees uniqueness even within the same ms
                .subject(userId)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }
    
    /**
     * Extract user ID from token
     */
    public String extractUserId(String token) {
        try {
            return extractAllClaims(token).getSubject();
        } catch (JwtException e) {
            logger.error("Failed to extract user ID from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract username from token
     */
    public String extractUsername(String token) {
        try {
            return extractAllClaims(token).get("username", String.class);
        } catch (JwtException e) {
            logger.error("Failed to extract username from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract roles from token
     */
    @SuppressWarnings("unchecked")
    public Set<String> extractRoles(String token) {
        try {
            Claims claims = extractAllClaims(token);
            List<String> rolesList = claims.get("roles", List.class);
            return new HashSet<>(rolesList != null ? rolesList : Collections.emptyList());
        } catch (JwtException e) {
            logger.error("Failed to extract roles from token: {}", e.getMessage());
            return Collections.emptySet();
        }
    }
    
    /**
     * Extract token type
     */
    public String extractTokenType(String token) {
        try {
            return extractAllClaims(token).get("type", String.class);
        } catch (JwtException e) {
            logger.error("Failed to extract token type: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract expiration date
     */
    public Date extractExpiration(String token) {
        try {
            return extractAllClaims(token).getExpiration();
        } catch (JwtException e) {
            logger.error("Failed to extract expiration from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Validate token
     */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            logger.debug("Token is expired: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            logger.warn("Token is malformed: {}", e.getMessage());
            return false;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            logger.warn("Invalid token signature: {}", e.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            logger.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            extractAllClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            return true;
        }
    }
    
    /**
     * Check if token is a refresh token
     */
    public boolean isRefreshToken(String token) {
        String tokenType = extractTokenType(token);
        return "refresh".equals(tokenType);
    }
    
    /**
     * Check if token is an access token
     */
    public boolean isAccessToken(String token) {
        String tokenType = extractTokenType(token);
        return "access".equals(tokenType);
    }
    
    /**
     * Extract all claims from token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    /**
     * Get token expiration in seconds
     */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration / 1000;
    }
    
    /**
     * Get refresh token expiration in seconds
     */
    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpiration / 1000;
    }
}
