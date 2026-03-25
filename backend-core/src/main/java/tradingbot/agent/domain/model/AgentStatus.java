package tradingbot.agent.domain.model;

/**
 * AgentStatus — lifecycle states for a trading agent.
 *
 * <p>Extracted as a top-level enum so that both the domain layer
 * ({@link tradingbot.agent.domain.model}) and the infrastructure layer
 * ({@link tradingbot.agent.infrastructure.repository.AgentEntity}) share a
 * single canonical definition.  {@code AgentEntity.AgentStatus} is kept as a
 * type-alias import for backward compatibility until the next cleanup cycle.
 *
 * <p>Valid state transitions:
 * <pre>
 * CREATED ──► INITIALIZING ──► ACTIVE
 *                                  │
 *                             ┌────┘
 *                             ▼
 *                           PAUSED
 *                             │
 *                        ┌────┘
 *                        ▼
 *                     STOPPED
 *                        │
 *               ERROR (auto-retry via Resilience4j)
 * </pre>
 */
public enum AgentStatus {

    /** Agent has been created but not yet initialised. */
    CREATED,

    /** Agent is loading market state and warming up indicators. */
    INITIALIZING,

    /** Agent is fully running, consuming market events and issuing orders. */
    ACTIVE,

    /** Agent is temporarily suspended; resumes on {@code resume()} call. */
    PAUSED,

    /** Agent has been permanently halted; terminal state. */
    STOPPED,

    /** Agent encountered a recoverable error; Resilience4j retry in progress. */
    ERROR,

    // --- Legacy aliases kept for compatibility with AgentEntity.AgentStatus ---

    /** @deprecated Use {@link #CREATED} or {@link #STOPPED} instead. */
    @Deprecated IDLE
}
