-- Flyway migration: create agent_performance table
CREATE TABLE IF NOT EXISTS agent_performance (
    agent_id        VARCHAR(255)      PRIMARY KEY,
    total_trades    INTEGER           NOT NULL DEFAULT 0,
    winning_trades  INTEGER           NOT NULL DEFAULT 0,
    losing_trades   INTEGER           NOT NULL DEFAULT 0,
    total_pnl       DOUBLE PRECISION  NOT NULL DEFAULT 0.0,
    win_rate        DOUBLE PRECISION  NOT NULL DEFAULT 0.0,
    max_drawdown    DOUBLE PRECISION  NOT NULL DEFAULT 0.0,
    peak_capital    DOUBLE PRECISION  NOT NULL DEFAULT 0.0,
    current_capital DOUBLE PRECISION  NOT NULL DEFAULT 0.0,
    average_win     DOUBLE PRECISION  NOT NULL DEFAULT 0.0,
    average_loss    DOUBLE PRECISION  NOT NULL DEFAULT 0.0,
    sharpe_ratio    DOUBLE PRECISION  NOT NULL DEFAULT 0.0,
    last_updated    TIMESTAMP
);
