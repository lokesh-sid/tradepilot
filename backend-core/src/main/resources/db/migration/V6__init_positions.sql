-- Flyway migration: create positions table
CREATE TABLE IF NOT EXISTS positions (
    id                  VARCHAR(255) PRIMARY KEY,
    agent_id            VARCHAR(255) NOT NULL,
    symbol              VARCHAR(32)  NOT NULL,
    direction           VARCHAR(10)  NOT NULL,
    entry_price         DOUBLE PRECISION NOT NULL,
    quantity            DOUBLE PRECISION NOT NULL,
    stop_loss           DOUBLE PRECISION,
    take_profit         DOUBLE PRECISION,
    main_order_id       VARCHAR(255) NOT NULL,
    stop_loss_order_id  VARCHAR(255),
    take_profit_order_id VARCHAR(255),
    status              VARCHAR(20)  NOT NULL,
    exit_price          DOUBLE PRECISION,
    realized_pnl        DOUBLE PRECISION,
    opened_at           TIMESTAMP    NOT NULL,
    closed_at           TIMESTAMP,
    last_checked_at     TIMESTAMP,
    last_unrealized_pnl DOUBLE PRECISION NOT NULL DEFAULT 0.0
);

CREATE INDEX IF NOT EXISTS idx_positions_agent_id  ON positions(agent_id);
CREATE INDEX IF NOT EXISTS idx_positions_symbol    ON positions(symbol);
CREATE INDEX IF NOT EXISTS idx_positions_status    ON positions(status);
CREATE INDEX IF NOT EXISTS idx_positions_opened_at ON positions(opened_at);
