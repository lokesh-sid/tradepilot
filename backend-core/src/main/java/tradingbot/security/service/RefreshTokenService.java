package tradingbot.security.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tradingbot.security.entity.RefreshToken;
import tradingbot.security.repository.RefreshTokenRepository;

/**
 * Manages the server-side lifecycle of refresh tokens.
 *
 * <p>Strategy (token-family rotation):
 * <ol>
 *   <li>On login/register a new family is created; the raw JWT is returned
 *       to the client and only its SHA-256 hash is persisted.</li>
 *   <li>On refresh the stored hash is looked up, the record is marked
 *       {@code used=true}, and a new token in the same family is issued.</li>
 *   <li>If a {@code used} token is presented again, the entire family is
 *       invalidated — the user must log in again.</li>
 *   <li>Logout revokes the whole family so no sibling token can be reused.</li>
 * </ol>
 */
@Service
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository repository;
    private final JwtService jwtService;

    @Value("${jwt.refresh-token-expiration:86400000}")
    private long refreshTokenExpirationMs;

    public RefreshTokenService(RefreshTokenRepository repository, JwtService jwtService) {
        this.repository = repository;
        this.jwtService = jwtService;
    }

    /**
     * Issues a brand-new refresh token and starts a new family.
     * Call this on login and registration.
     *
     * @return raw JWT string to hand to the client
     */
    @Transactional
    public String createRefreshToken(String userId) {
        return persist(userId, UUID.randomUUID().toString());
    }

    /**
     * Validates the incoming token against the DB, marks it as used,
     * and issues a new token in the same family.
     *
     * <p>Throws {@link IllegalArgumentException} if the token is unknown,
     * expired, or has already been rotated (reuse detection).
     *
     * @return new raw JWT string to hand to the client
     */
    @Transactional
    public String validateAndRotate(String rawToken) {
        String hash = hash(rawToken);

        RefreshToken stored = repository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            repository.delete(stored);
            throw new IllegalArgumentException("Refresh token expired");
        }

        if (stored.isUsed()) {
            // Token reuse detected — revoke the whole family immediately
            logger.warn("Refresh token reuse detected for user {} — revoking family {}",
                    stored.getUserId(), stored.getFamilyId());
            repository.deleteByFamilyId(stored.getFamilyId());
            throw new IllegalArgumentException("Refresh token already used");
        }

        stored.setUsed(true);
        repository.save(stored);

        return persist(stored.getUserId(), stored.getFamilyId());
    }

    /**
     * Revokes the family that owns {@code rawToken}.
     * Safe to call with an unknown or already-expired token (no-op).
     */
    @Transactional
    public void revokeByToken(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(t -> repository.deleteByFamilyId(t.getFamilyId()));
    }

    /**
     * Revokes all refresh tokens for a user (e.g. on password change or
     * security event).
     */
    @Transactional
    public void revokeAllForUser(String userId) {
        repository.deleteByUserId(userId);
    }

    /** Scheduled cleanup of rows whose JWT expiry has passed. */
    @Scheduled(fixedRateString = "${auth.refresh-token.cleanup-interval-ms:3600000}")
    @Transactional
    public void cleanupExpired() {
        repository.deleteByExpiresAtBefore(Instant.now());
        logger.debug("Expired refresh tokens purged");
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private String persist(String userId, String familyId) {
        String rawToken = jwtService.generateRefreshToken(userId);

        RefreshToken entity = new RefreshToken();
        entity.setTokenHash(hash(rawToken));
        entity.setUserId(userId);
        entity.setFamilyId(familyId);
        entity.setExpiresAt(Instant.now().plusMillis(refreshTokenExpirationMs));
        repository.save(entity);

        return rawToken;
    }

    private String hash(String token) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
