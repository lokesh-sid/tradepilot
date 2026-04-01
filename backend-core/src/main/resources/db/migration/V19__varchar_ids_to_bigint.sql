-- V19: Convert all numeric ID columns from VARCHAR(255) to BIGINT.
--
-- Motivation: entity IDs are 64-bit integers. Storing them as BIGINT gives a
-- ~50 % reduction in on-disk size per ID column and allows the B-tree index to
-- use integer comparison instead of varchar collation, improving insert
-- throughput and range-scan latency.
--
-- Safety: the application has not been deployed to production, so all tables
-- are empty. The USING clause is retained for correctness; it is a no-op on
-- empty tables.
--
-- Changes (one ALTER per column for clarity and atomic rollback granularity):

DO $$
BEGIN
    -- Ensure tables are empty to avoid cast exceptions from non-numeric UUIDs.
    --
    -- agents is checked as a proxy for all tables that carry agent_id FKs
    -- (orders, positions, trade_journal, trading_experiences, agent_performance,
    -- chat_messages): a non-empty agents table implies those dependent tables may
    -- also be populated, and an empty agents table guarantees FK-constrained
    -- dependents are empty too.
    --
    -- dead_letter_events and trading_events / event_metadata have no FK to
    -- agents, so they are checked explicitly.
    IF (SELECT COUNT(*) FROM agents) > 0 THEN
        RAISE EXCEPTION 'V19 migration is destructive and cannot be run on non-empty tables (agents). Manual migration of string IDs required.';
    END IF;
    IF (SELECT COUNT(*) FROM dead_letter_events) > 0 THEN
        RAISE EXCEPTION 'V19 migration is destructive and cannot be run on non-empty tables (dead_letter_events). Manual migration of string IDs required.';
    END IF;
    IF (SELECT COUNT(*) FROM trading_events) > 0 THEN
        RAISE EXCEPTION 'V19 migration is destructive and cannot be run on non-empty tables (trading_events). Manual migration of string IDs required.';
    END IF;
END $$;

-- ── agents ────────────────────────────────────────────────────────────────────
ALTER TABLE agents ALTER COLUMN id TYPE BIGINT USING id::BIGINT;

-- ── orders ───────────────────────────────────────────────────────────────────
ALTER TABLE orders ALTER COLUMN id          TYPE BIGINT USING id::BIGINT;
ALTER TABLE orders ALTER COLUMN executor_id TYPE BIGINT USING executor_id::BIGINT;

-- ── positions ────────────────────────────────────────────────────────────────
ALTER TABLE positions ALTER COLUMN id                   TYPE BIGINT USING id::BIGINT;
ALTER TABLE positions ALTER COLUMN agent_id             TYPE BIGINT USING agent_id::BIGINT;
ALTER TABLE positions ALTER COLUMN main_order_id        TYPE BIGINT USING main_order_id::BIGINT;
ALTER TABLE positions ALTER COLUMN stop_loss_order_id   TYPE BIGINT USING stop_loss_order_id::BIGINT;
ALTER TABLE positions ALTER COLUMN take_profit_order_id TYPE BIGINT USING take_profit_order_id::BIGINT;

-- ── trade_journal ─────────────────────────────────────────────────────────────
ALTER TABLE trade_journal ALTER COLUMN id             TYPE BIGINT USING id::BIGINT;
ALTER TABLE trade_journal ALTER COLUMN agent_id       TYPE BIGINT USING agent_id::BIGINT;
ALTER TABLE trade_journal ALTER COLUMN entry_order_id TYPE BIGINT USING entry_order_id::BIGINT;

-- ── trading_experiences (TradeMemoryEntity) ───────────────────────────────────
ALTER TABLE trading_experiences ALTER COLUMN id       TYPE BIGINT USING id::BIGINT;
ALTER TABLE trading_experiences ALTER COLUMN agent_id TYPE BIGINT USING agent_id::BIGINT;

-- ── dead_letter_events ───────────────────────────────────────────────────────
ALTER TABLE dead_letter_events ALTER COLUMN id TYPE BIGINT USING id::BIGINT;

-- ── agent_performance ────────────────────────────────────────────────────────
ALTER TABLE agent_performance ALTER COLUMN agent_id TYPE BIGINT USING agent_id::BIGINT;

-- ── trading_events + event_metadata ──────────────────────────────────────────
-- event_metadata.event_id is a FK that references trading_events.id (the PK).
-- Both must be altered together; the FK must be dropped and re-added.
ALTER TABLE event_metadata DROP CONSTRAINT IF EXISTS event_metadata_event_id_fkey;
ALTER TABLE event_metadata  ALTER COLUMN event_id TYPE BIGINT USING event_id::BIGINT;
ALTER TABLE trading_events  ALTER COLUMN id       TYPE BIGINT USING id::BIGINT;
ALTER TABLE event_metadata
    ADD CONSTRAINT event_metadata_event_id_fkey
    FOREIGN KEY (event_id) REFERENCES trading_events(id);

-- ── chat_messages ─────────────────────────────────────────────────────────────
-- PK (id BIGSERIAL) is already BIGINT; only the agent_id FK column changes.
ALTER TABLE chat_messages ALTER COLUMN agent_id TYPE BIGINT USING agent_id::BIGINT;
