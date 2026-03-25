-- Flyway migration: create refresh_tokens table for server-side refresh token storage
-- Enables refresh token rotation with reuse detection (token-family approach)
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id              VARCHAR(255) PRIMARY KEY,
    token_hash      VARCHAR(64) NOT NULL UNIQUE, -- SHA-256 hex of the raw JWT
    user_id         VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id       VARCHAR(255) NOT NULL, -- all rotated siblings share this ID
    used            BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rt_token_hash ON refresh_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_rt_user_id    ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_rt_family_id  ON refresh_tokens(family_id);
CREATE INDEX IF NOT EXISTS idx_rt_expires_at ON refresh_tokens(expires_at);
