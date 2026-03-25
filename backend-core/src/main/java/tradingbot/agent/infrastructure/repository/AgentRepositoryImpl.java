package tradingbot.agent.infrastructure.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.model.AgentSymbolLink;
import tradingbot.agent.domain.model.PageResult;
import tradingbot.agent.domain.repository.AgentRepository;

/**
 * AgentRepositoryImpl - Implementation of domain AgentRepository using Spring Data JPA
 */
@Component
public class AgentRepositoryImpl implements AgentRepository {
    
    private final JpaAgentRepository jpaRepository;
    
    public AgentRepositoryImpl(JpaAgentRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }
    
    @Override
    public Agent save(Agent agent) {
        AgentEntity entity = AgentMapper.toEntity(agent);
        AgentEntity saved = jpaRepository.save(entity);
        return AgentMapper.toDomain(saved);
    }
    
    @Override
    public Optional<Agent> findById(AgentId id) {
        return jpaRepository.findById(id.getValue())
            .map(AgentMapper::toDomain);
    }
    
    @Override
    public Optional<Agent> findByName(String name) {
        return jpaRepository.findByName(name)
            .map(AgentMapper::toDomain);
    }
    
    @Override
    public List<Agent> findAll() {
        return jpaRepository.findAll().stream()
            .map(AgentMapper::toDomain)
            .collect(Collectors.toList());
    }
    
    @Override
    public PageResult<Agent> findByOwner(String ownerId, int offset, int limit) {
        int page = offset / Math.max(limit, 1);
        var jpaPage = jpaRepository.findByOwnerId(ownerId, PageRequest.of(page, limit));
        List<Agent> content = jpaPage.map(AgentMapper::toDomain).getContent();
        return new PageResult<>(content, jpaPage.getTotalElements());
    }

    @Override
    public List<Agent> findAllActive() {
        return jpaRepository.findAllActive().stream()
            .map(AgentMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<AgentSymbolLink> findActiveAgentSymbols() {
        return jpaRepository.findAllActiveAgentSymbols().stream()
            .map(p -> new AgentSymbolLink(p.getId(), p.getTradingSymbol()))
            .toList();
    }
    
    @Override
    public void delete(AgentId id) {
        jpaRepository.deleteById(id.getValue());
    }
    
    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }
}
