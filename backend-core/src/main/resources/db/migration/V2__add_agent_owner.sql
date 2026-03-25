-- Migration for both PostgreSQL and H2 compatibility
-- Adds owner_id column to agents table
-- Only add the column if it does not already exist (PostgreSQL)
DO $$
BEGIN
	IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='agents' AND column_name='owner_id') THEN
		ALTER TABLE agents ADD COLUMN owner_id VARCHAR(255);
	END IF;
END$$;
