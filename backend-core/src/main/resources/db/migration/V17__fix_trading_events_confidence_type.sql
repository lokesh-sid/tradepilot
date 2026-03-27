-- Flyway migration: align trading_events.confidence with entity mapping
-- TradeSignalEventEntity declares confidence as Double, but V5 created it as INTEGER.
ALTER TABLE trading_events ALTER COLUMN confidence TYPE DOUBLE PRECISION;
