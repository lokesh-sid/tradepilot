package tradingbot.agent.application.event;

import tradingbot.agent.domain.model.AgentId;

/**
 * Published when an agent is stopped, paused, or deleted, so the orchestrator
 * can evict it from the in-memory routing caches immediately.
 */
public record AgentStoppedEvent(AgentId agentId) {}
