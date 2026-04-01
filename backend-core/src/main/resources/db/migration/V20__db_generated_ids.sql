-- V20: Enable database-generated BIGINT identity on all entity primary keys.
--
-- V19 converted PK columns from VARCHAR(255) to BIGINT.
-- V20 adds GENERATED ALWAYS AS IDENTITY so the database assigns IDs at INSERT
-- time; the application no longer generates IDs before saving.
--
-- Each DO block seeds the identity sequence above the current maximum row ID
-- to prevent collisions on environments that already contain data rows.
-- On empty tables COALESCE(MAX(id), 0) + 1 evaluates to 1.

-- ── agents ────────────────────────────────────────────────────────────────────
DO $$
DECLARE next_val BIGINT;
BEGIN
  SELECT COALESCE(MAX(id), 0) + 1 INTO next_val FROM agents;
  EXECUTE format('ALTER TABLE agents ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH %s)', next_val);
END $$;

-- ── orders ───────────────────────────────────────────────────────────────────
DO $$
DECLARE next_val BIGINT;
BEGIN
  SELECT COALESCE(MAX(id), 0) + 1 INTO next_val FROM orders;
  EXECUTE format('ALTER TABLE orders ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH %s)', next_val);
END $$;

-- ── positions ────────────────────────────────────────────────────────────────
DO $$
DECLARE next_val BIGINT;
BEGIN
  SELECT COALESCE(MAX(id), 0) + 1 INTO next_val FROM positions;
  EXECUTE format('ALTER TABLE positions ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH %s)', next_val);
END $$;

-- ── trade_journal ─────────────────────────────────────────────────────────────
DO $$
DECLARE next_val BIGINT;
BEGIN
  SELECT COALESCE(MAX(id), 0) + 1 INTO next_val FROM trade_journal;
  EXECUTE format('ALTER TABLE trade_journal ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH %s)', next_val);
END $$;

-- ── trading_experiences (TradeMemoryEntity) ───────────────────────────────────
DO $$
DECLARE next_val BIGINT;
BEGIN
  SELECT COALESCE(MAX(id), 0) + 1 INTO next_val FROM trading_experiences;
  EXECUTE format('ALTER TABLE trading_experiences ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH %s)', next_val);
END $$;

-- ── dead_letter_events ───────────────────────────────────────────────────────
DO $$
DECLARE next_val BIGINT;
BEGIN
  SELECT COALESCE(MAX(id), 0) + 1 INTO next_val FROM dead_letter_events;
  EXECUTE format('ALTER TABLE dead_letter_events ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH %s)', next_val);
END $$;

-- ── trading_events ────────────────────────────────────────────────────────────
DO $$
DECLARE next_val BIGINT;
BEGIN
  SELECT COALESCE(MAX(id), 0) + 1 INTO next_val FROM trading_events;
  EXECUTE format('ALTER TABLE trading_events ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH %s)', next_val);
END $$;
