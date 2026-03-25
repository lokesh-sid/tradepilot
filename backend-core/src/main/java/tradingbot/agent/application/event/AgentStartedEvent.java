package tradingbot.agent.application.event;

import tradingbot.agent.domain.model.AgentId;

/**
 * Published when an agent is activated, so the orchestrator can update
 * its in-memory {@code symbolToAgentMap} without a full DB scan.
 */
public record AgentStartedEvent(AgentId agentId, String symbol) {}
