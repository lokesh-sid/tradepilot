package tradingbot.agent.domain.repository;

import java.util.List;
import java.util.Optional;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.model.AgentSymbolLink;
import tradingbot.agent.domain.model.PageResult;

/**
 * AgentRepository - Repository interface for Agent aggregate
 * 
 * Domain repository following DDD principles
 */
public interface AgentRepository {
    
    /**
     * Save an agent (create or update)
     */
    Agent save(Agent agent);
    
    /**
     * Find agent by ID
     */
    Optional<Agent> findById(AgentId id);
    
    /**
     * Find agent by name
     */
    Optional<Agent> findByName(String name);
    
    /**
     * Find all agents
     */
    List<Agent> findAll();
    
    /**
     * Find agents owned by a specific user (paginated)
     *
     * @param ownerId the owner's identifier
     * @param offset  zero-based offset of the first result
     * @param limit   maximum number of results to return
     * @return page result containing agents and total count
     */
    PageResult<Agent> findByOwner(String ownerId, int offset, int limit);

    /**
     * Find all active agents (status = ACTIVE)
     */
    List<Agent> findAllActive();

    /**
     * Lightweight projection returning only agent IDs and symbols for active agents.
     * Used by the orchestrator to rebuild routing caches without hydrating full entities.
     */
    List<AgentSymbolLink> findActiveAgentSymbols();
    
    /**
     * Delete an agent
     */
    void delete(AgentId id);
    
    /**
     * Check if agent with name exists
     */
    boolean existsByName(String name);
}
