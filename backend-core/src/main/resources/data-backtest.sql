-- Backtest profile seed data
-- Seeds one ACTIVE agent so AgentOrchestrator's 30s polling loop
-- has something to run, triggering LLM → CachedGrokService → file cache.
-- The agent uses hardcoded stub market data (LegacyLLMStrategy.getMarketData
-- returns 45000.0/UPTREND/BULLISH) so the cache key is deterministic on first run.

INSERT INTO agents (
    id,
    name,
    goal_type,
    goal_description,
    trading_symbol,
    capital,
    status,
    created_at,
    iteration_count
) VALUES (
    'backtest-agent-001',
    'BacktestAgent-BTC',
    'MAXIMIZE_PROFIT',
    'Backtest agent for BTCUSDT — exercises the full LLM reasoning pipeline against cached responses to avoid real API calls.',
    'BTCUSDT',
    10000.0,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    0
);
