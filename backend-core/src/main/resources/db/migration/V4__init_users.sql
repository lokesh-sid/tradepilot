-- Flyway migration: create users and user_roles tables
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    last_login TIMESTAMP,
    last_password_change TIMESTAMP,
    oauth_provider VARCHAR(20),
    oauth_id VARCHAR(255),
    max_bots INTEGER NOT NULL DEFAULT 10,
    max_api_calls_per_hour INTEGER NOT NULL DEFAULT 1000,
    max_positions_per_bot INTEGER NOT NULL DEFAULT 5,
    max_leverage INTEGER NOT NULL DEFAULT 100,
    account_tier VARCHAR(20) NOT NULL DEFAULT 'FREE',
    subscription_expires_at TIMESTAMP,
    api_key VARCHAR(64) UNIQUE,
    api_key_enabled BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id VARCHAR(255) NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_oauth ON users(oauth_provider, oauth_id);
