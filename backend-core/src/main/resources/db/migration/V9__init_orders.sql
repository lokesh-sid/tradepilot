-- Flyway migration: create orders table
CREATE TABLE IF NOT EXISTS orders (
    id                  VARCHAR(255)      PRIMARY KEY,
    agent_id            VARCHAR(255)      NOT NULL,
    symbol              VARCHAR(255)      NOT NULL,
    direction           VARCHAR(10)       NOT NULL,
    price               DOUBLE PRECISION  NOT NULL,
    quantity            DOUBLE PRECISION  NOT NULL,
    stop_loss           DOUBLE PRECISION,
    take_profit         DOUBLE PRECISION,
    leverage            INTEGER,
    status              VARCHAR(20)       NOT NULL,
    created_at          TIMESTAMP         NOT NULL,
    executed_at         TIMESTAMP,
    exchange_order_id   VARCHAR(255),
    failure_reason      VARCHAR(1000),
    realized_pnl        DOUBLE PRECISION
);

CREATE INDEX IF NOT EXISTS idx_orders_agent_id  ON orders(agent_id);
CREATE INDEX IF NOT EXISTS idx_orders_symbol    ON orders(symbol);
CREATE INDEX IF NOT EXISTS idx_orders_status    ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);
