-- Flyway migration: create risk_events table
CREATE TABLE IF NOT EXISTS risk_events (
    id            BIGSERIAL        PRIMARY KEY,
    risk_type     VARCHAR(255),
    risk_level    VARCHAR(20),
    description   VARCHAR(500),
    current_price DOUBLE PRECISION,
    trigger_value DOUBLE PRECISION,
    action_taken  VARCHAR(255)
);
