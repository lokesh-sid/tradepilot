-- Flyway migration: create trading_experiences table
CREATE TABLE IF NOT EXISTS trading_experiences (
    id                   VARCHAR(255)     PRIMARY KEY,
    agent_id             VARCHAR(255)     NOT NULL,
    symbol               VARCHAR(255)     NOT NULL,
    scenario_description VARCHAR(2000)    NOT NULL,
    direction            VARCHAR(10)      NOT NULL,
    entry_price          DOUBLE PRECISION NOT NULL,
    exit_price           DOUBLE PRECISION,
    outcome              VARCHAR(20)      NOT NULL,
    profit_percent       DOUBLE PRECISION,
    lesson_learned       VARCHAR(2000),
    timestamp            TIMESTAMP        NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_experiences_agent_id  ON trading_experiences(agent_id);
CREATE INDEX IF NOT EXISTS idx_experiences_symbol    ON trading_experiences(symbol);
CREATE INDEX IF NOT EXISTS idx_experiences_outcome   ON trading_experiences(outcome);
CREATE INDEX IF NOT EXISTS idx_experiences_timestamp ON trading_experiences(timestamp);
