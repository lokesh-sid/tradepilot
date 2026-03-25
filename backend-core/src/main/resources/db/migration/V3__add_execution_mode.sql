-- Add execution_mode column to separate bot execution mode from domain goal type.
-- Rows previously using 'FUTURES_PAPER' or 'FUTURES' as goal_type get migrated:
--   - execution_mode = their old goal_type value (FUTURES_PAPER / FUTURES)
--   - goal_type is reset to 'MAXIMIZE_PROFIT' (a real domain GoalType)
-- Rows that already had a proper domain goal_type get execution_mode = 'NONE'.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='agents' AND column_name='execution_mode') THEN
        ALTER TABLE agents ADD COLUMN execution_mode VARCHAR(50) NOT NULL DEFAULT 'NONE';
    END IF;
END$$;

-- Migrate rows where goal_type was used as execution mode
UPDATE agents SET execution_mode = 'FUTURES_PAPER', goal_type = 'MAXIMIZE_PROFIT'
  WHERE goal_type = 'FUTURES_PAPER';

UPDATE agents SET execution_mode = 'FUTURES', goal_type = 'MAXIMIZE_PROFIT'
  WHERE goal_type = 'FUTURES';
