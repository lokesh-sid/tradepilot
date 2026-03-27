-- Phase 2: Extend orders table for executor abstraction.
-- Renames agent_id to executor_id, adds executor_type to disambiguate
-- agent vs bot orders, and adds bot-specific limit order columns.

-- Step 1: Add executor_id and back-fill from agent_id
ALTER TABLE orders ADD COLUMN executor_id VARCHAR(255);
UPDATE orders SET executor_id = agent_id;
ALTER TABLE orders ALTER COLUMN executor_id SET NOT NULL;

-- Step 2: Add executor_type and back-fill all existing rows as AGENT
ALTER TABLE orders ADD COLUMN executor_type VARCHAR(10);
UPDATE orders SET executor_type = 'AGENT';
ALTER TABLE orders ALTER COLUMN executor_type SET NOT NULL;

-- Step 3: Drop the old agent_id column and its index
DROP INDEX IF EXISTS idx_orders_agent_id;
ALTER TABLE orders DROP COLUMN agent_id;

-- Step 4: Create replacement index on executor_id
CREATE INDEX IF NOT EXISTS idx_orders_executor_id ON orders(executor_id);

-- Step 5: Add order_type (MARKET/LIMIT); back-fill existing rows as MARKET
ALTER TABLE orders ADD COLUMN order_type VARCHAR(10);
UPDATE orders SET order_type = 'MARKET';
ALTER TABLE orders ALTER COLUMN order_type SET NOT NULL;

-- Step 6: Add bot-specific limit order columns (nullable — agents never populate these)
ALTER TABLE orders ADD COLUMN limit_price        DOUBLE PRECISION;
ALTER TABLE orders ADD COLUMN filled_quantity    DOUBLE PRECISION;
ALTER TABLE orders ADD COLUMN remaining_quantity DOUBLE PRECISION;
