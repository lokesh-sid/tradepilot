-- Flyway migration: create chat_messages table
CREATE TABLE IF NOT EXISTS chat_messages (
    id          BIGSERIAL        PRIMARY KEY,
    agent_id    VARCHAR(255)     NOT NULL,
    message_json TEXT            NOT NULL,
    timestamp   TIMESTAMP        NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_agent_id  ON chat_messages(agent_id);
CREATE INDEX IF NOT EXISTS idx_chat_timestamp ON chat_messages(timestamp);
