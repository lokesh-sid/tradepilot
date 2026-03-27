-- Flyway migration: create dead_letter_events table
CREATE TABLE IF NOT EXISTS dead_letter_events (
    id                  VARCHAR(255)    PRIMARY KEY,
    original_topic      VARCHAR(255)    NOT NULL,
    kafka_partition     INTEGER         NOT NULL,
    kafka_offset        BIGINT          NOT NULL,
    message_key         VARCHAR(255),
    message_value       VARCHAR(4000),
    exception_type      VARCHAR(255)    NOT NULL,
    exception_message   VARCHAR(1000),
    stack_trace         VARCHAR(2000),
    received_at         TIMESTAMP       NOT NULL,
    resolved            BOOLEAN         NOT NULL DEFAULT FALSE,
    resolved_at         TIMESTAMP,
    resolution_note     VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_dle_topic       ON dead_letter_events(original_topic);
CREATE INDEX IF NOT EXISTS idx_dle_received_at ON dead_letter_events(received_at);
CREATE INDEX IF NOT EXISTS idx_dle_resolved    ON dead_letter_events(resolved);
CREATE INDEX IF NOT EXISTS idx_dle_exception   ON dead_letter_events(exception_type);
