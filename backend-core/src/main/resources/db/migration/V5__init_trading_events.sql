-- Flyway migration: create trading_events and event_metadata tables
-- Uses SINGLE_TABLE inheritance: all subclasses (GENERIC, TRADE_EXECUTED, TRADE_SIGNAL)
-- share one table, distinguished by the event_type discriminator column.

CREATE TABLE IF NOT EXISTS trading_events (
    id            VARCHAR(255) PRIMARY KEY,
    event_id      VARCHAR(255) NOT NULL UNIQUE,
    timestamp     TIMESTAMP    NOT NULL,
    bot_id        VARCHAR(255) NOT NULL,
    event_type    VARCHAR(50)  NOT NULL,
    symbol        VARCHAR(32),
    created_at    TIMESTAMP    NOT NULL,

    -- TradeExecutionEventEntity columns
    order_id      VARCHAR(255),
    trade_id      VARCHAR(255),
    side          VARCHAR(10),
    quantity      DOUBLE PRECISION,
    price         DOUBLE PRECISION,
    status        VARCHAR(50),
    profit_loss   DOUBLE PRECISION,
    commission    DOUBLE PRECISION,

    -- TradeSignalEventEntity columns
    signal_direction VARCHAR(10),
    confidence       INTEGER,
    current_price    DOUBLE PRECISION,
    indicators       VARCHAR(2000)
);

CREATE INDEX IF NOT EXISTS idx_bot_id    ON trading_events(bot_id);
CREATE INDEX IF NOT EXISTS idx_event_type ON trading_events(event_type);
CREATE INDEX IF NOT EXISTS idx_timestamp  ON trading_events(timestamp);
CREATE INDEX IF NOT EXISTS idx_symbol     ON trading_events(symbol);

-- Key-value metadata bag linked to each event
CREATE TABLE IF NOT EXISTS event_metadata (
    event_id       VARCHAR(255) NOT NULL REFERENCES trading_events(id),
    metadata_key   VARCHAR(255) NOT NULL,
    metadata_value VARCHAR(1000)
);
