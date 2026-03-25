package tradingbot.agent.application.event;

import tradingbot.agent.domain.model.AgentId;

/**
 * Published when an agent is paused, so the orchestrator can evict it from
 * routing caches.  Distinct from {@link AgentStoppedEvent} to allow listeners
 * to differentiate temporary pauses from permanent stops.
 */
public record AgentPausedEvent(AgentId agentId) {}
