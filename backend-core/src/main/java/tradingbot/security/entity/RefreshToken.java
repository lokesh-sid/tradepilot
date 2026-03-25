package tradingbot.security.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Persisted refresh token for server-side validation and revocation.
 *
 * Only a SHA-256 hash of the raw JWT is stored — the plaintext token
 * never touches the database.  All tokens that belong to one login
 * session share the same {@code familyId}.  If a token that has
 * already been rotated (used=true) is presented again, the entire
 * family is immediately revoked to neutralise a stolen-token replay.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_rt_token_hash", columnList = "token_hash"),
    @Index(name = "idx_rt_user_id",    columnList = "user_id"),
    @Index(name = "idx_rt_family_id",  columnList = "family_id"),
    @Index(name = "idx_rt_expires_at", columnList = "expires_at")
})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "family_id", nullable = false)
    private String familyId;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getFamilyId() { return familyId; }
    public void setFamilyId(String familyId) { this.familyId = familyId; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
