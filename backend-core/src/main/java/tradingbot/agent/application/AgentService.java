package tradingbot.agent.application;

import static org.springframework.security.core.context.SecurityContextHolder.*;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tradingbot.agent.api.dto.AgentMapper;
import tradingbot.agent.api.dto.AgentResponse;
import tradingbot.agent.api.dto.CreateAgentRequest;
import tradingbot.agent.api.dto.PaginatedAgentResponse;
import tradingbot.agent.application.event.AgentPausedEvent;
import tradingbot.agent.application.event.AgentStartedEvent;
import tradingbot.agent.application.event.AgentStoppedEvent;
import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.model.PageResult;
import tradingbot.agent.domain.repository.AgentRepository;

/**
 * AgentService - Application service for agent management
 */
@Service
@Transactional
public class AgentService {
    
    private final AgentRepository agentRepository;
    private final AgentMapper agentMapper;
    private final ApplicationEventPublisher eventPublisher;
    
    public AgentService(AgentRepository agentRepository, AgentMapper agentMapper,
                        ApplicationEventPublisher eventPublisher) {
        this.agentRepository = agentRepository;
        this.agentMapper = agentMapper;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Create a new agent
     */
    @CacheEvict(value = "agentsByOwner", key = "#result.ownerId")
    public Agent createAgent(CreateAgentRequest request) {
        // Check if agent with same name already exists
        if (agentRepository.existsByName(request.name())) {
            throw new AgentAlreadyExistsException("Agent with name '" + request.name() + "' already exists");
        }
        
        String ownerId = getContext().getAuthentication().getName();

        // Create and save agent using MapStruct mapper
        Agent agent = agentMapper.toDomain(request, ownerId);
        return agentRepository.save(agent);
    }
    
    /**
     * Get all agents (internal/admin use — no owner filtering)
     */
    public List<Agent> getAllAgents() {
        return agentRepository.findAll();
    }

    /**
     * Get agents owned by the authenticated user (paginated, cached).
     *
     * <p>A single DB round-trip fetches both the page content and total count.
     * Results are cached per-owner/page/size in Redis under the
     * {@code agentsByOwner} cache.  The cache is evicted when the owner
     * creates, updates, or deletes an agent.
     *
     * @param ownerId the authenticated user's identifier
     * @param page    zero-based page index
     * @param size    page size (max results per page)
     * @return paginated response with agent list and total count
     */
    @Cacheable(value = "agentsByOwner", key = "#ownerId + ':' + #page + ':' + #size")
    @Transactional(readOnly = true)
    public PaginatedAgentResponse getAgentsByOwner(String ownerId, int page, int size) {
        PageResult<Agent> result = agentRepository.findByOwner(ownerId, page * size, size);
        List<AgentResponse> responses = result.content().stream()
            .map(agentMapper::toResponse)
            .toList();
        return new PaginatedAgentResponse(responses, page, size, result.totalElements());
    }
    
    /**
     * Get agent by ID
     */
    public Agent getAgent(AgentId id) {
        return agentRepository.findById(id)
            .orElseThrow(() -> new AgentNotFoundException("Agent not found: " + id.getValue()));
    }
    
    /**
     * Activate an agent
     */
    @CacheEvict(value = "agentsByOwner", allEntries = true)
    public Agent activateAgent(AgentId id) {
        Agent agent = getAgent(id);
        agent.activate();
        Agent saved = agentRepository.save(agent);
        eventPublisher.publishEvent(new AgentStartedEvent(id, agent.getTradingSymbol()));
        return saved;
    }
    
    /**
     * Pause an agent
     */
    @CacheEvict(value = "agentsByOwner", allEntries = true)
    public Agent pauseAgent(AgentId id) {
        Agent agent = getAgent(id);
        agent.pause();
        agentRepository.save(agent);
        eventPublisher.publishEvent(new AgentPausedEvent(id));
        return agent;
    }
    
    /**
     * Stop an agent
     */
    @CacheEvict(value = "agentsByOwner", allEntries = true)
    public void stopAgent(AgentId id) {
        Agent agent = getAgent(id);
        agent.stop();
        agentRepository.save(agent);
        eventPublisher.publishEvent(new AgentStoppedEvent(id));
    }

    /**
     * Stop and delete an agent
     */
    @CacheEvict(value = "agentsByOwner", allEntries = true)
    public void deleteAgent(AgentId id) {
        // Stop the agent first (will throw AgentNotFoundException if not found)
        stopAgent(id);
        
        // Then delete
        agentRepository.delete(id);
    }
}
