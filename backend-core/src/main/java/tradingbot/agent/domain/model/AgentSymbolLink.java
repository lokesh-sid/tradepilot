package tradingbot.agent.domain.model;

/**
 * Lightweight projection carrying only the agent identifier and its trading
 * symbol.  Used by the orchestrator to rebuild the {@code symbolToAgentMap}
 * without hydrating full {@link Agent} entities.
 */
public record AgentSymbolLink(String agentId, String symbol) {}
