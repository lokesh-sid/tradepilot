-- Flyway migration: create trade_journal table
--
-- A single, canonical record for every trade decision: entry context, outcome,
-- LLM lesson, and human journal annotations in one query-friendly table.
--
-- Write path:
--   ENTRY  → TradeExecutionService.record() on ENTER_LONG / ENTER_SHORT
--   CLOSE  → TradeReflectionListener.onTradeCompleted() after lesson generation
--   ANNOTATE → JournalController PATCH /api/journal/{id} (human or UI)
--   REVIEW → LLMStrategyReviewService weekly batch (llm_batch_analysis, flagged_for_review)

CREATE TABLE IF NOT EXISTS trade_journal (
    id                   VARCHAR(255)      PRIMARY KEY,

    -- Identity
    agent_id             VARCHAR(255)      NOT NULL,
    symbol               VARCHAR(32)       NOT NULL,
    entry_order_id       VARCHAR(255),                     -- FK to orders.id

    -- Decision context (written at entry — ephemeral, not derivable later)
    direction            VARCHAR(10)       NOT NULL,        -- LONG / SHORT
    entry_price          DOUBLE PRECISION  NOT NULL,
    quantity             DOUBLE PRECISION  NOT NULL,
    stop_loss            DOUBLE PRECISION,
    take_profit          DOUBLE PRECISION,
    confidence           INTEGER,                          -- 0–100 from AgentDecision
    llm_reasoning        TEXT,                             -- AgentDecision.reasoning()
    decided_at           TIMESTAMP         NOT NULL,

    -- Outcome (written at close via TradeReflectionListener)
    exit_price           DOUBLE PRECISION,
    realized_pnl         DOUBLE PRECISION,
    pnl_percent          DOUBLE PRECISION,
    outcome              VARCHAR(20)       NOT NULL DEFAULT 'PENDING',  -- TradeOutcome enum
    close_reason         VARCHAR(30),                      -- TAKE_PROFIT / STOP_LOSS / MANUAL / LIQUIDATED
    llm_lesson           TEXT,                             -- TradeReflectionService output
    closed_at            TIMESTAMP,

    -- Human journal fields (written via PATCH /api/journal/{id})
    notes                TEXT,
    tags                 VARCHAR(1000),                    -- comma-separated, e.g. "fomo,late-entry"
    conviction           SMALLINT CHECK (conviction BETWEEN 1 AND 5),
    reviewed_at          TIMESTAMP,
    review_notes         TEXT,

    -- Feedback loop fields (written by LLMStrategyReviewService)
    llm_batch_analysis   TEXT,
    flagged_for_review   BOOLEAN           NOT NULL DEFAULT FALSE,

    created_at           TIMESTAMP         NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_journal_agent_id    ON trade_journal(agent_id);
CREATE INDEX IF NOT EXISTS idx_journal_symbol      ON trade_journal(symbol);
CREATE INDEX IF NOT EXISTS idx_journal_outcome     ON trade_journal(outcome);
CREATE INDEX IF NOT EXISTS idx_journal_decided_at  ON trade_journal(decided_at);
CREATE INDEX IF NOT EXISTS idx_journal_flagged     ON trade_journal(flagged_for_review) WHERE flagged_for_review = TRUE;

-- Composite: hot-path trade-close lookup (agent + symbol + outcome + sort key)
CREATE INDEX IF NOT EXISTS idx_journal_agent_symbol_outcome
    ON trade_journal(agent_id, symbol, outcome, decided_at DESC);

-- Composite: stats and filtered-list queries that filter on agent + outcome
CREATE INDEX IF NOT EXISTS idx_journal_agent_outcome
    ON trade_journal(agent_id, outcome);
