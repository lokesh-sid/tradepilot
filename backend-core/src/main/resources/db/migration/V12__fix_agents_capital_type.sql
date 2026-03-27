-- Flyway migration: align agents.capital column type with entity mapping
-- AgentEntity declares capital as double (float8), but V1 created it as NUMERIC(18,2).
-- Changing to DOUBLE PRECISION so Hibernate validate passes without data loss.
ALTER TABLE agents ALTER COLUMN capital TYPE DOUBLE PRECISION;
