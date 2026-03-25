-- Add exchange_name to agents table to support per-agent exchange selection.
-- NULL means the agent uses the globally configured exchange provider (trading.exchange.provider).
ALTER TABLE agents ADD COLUMN IF NOT EXISTS exchange_name VARCHAR(50);
