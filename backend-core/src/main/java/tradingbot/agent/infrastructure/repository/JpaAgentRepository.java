package tradingbot.agent.infrastructure.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * JpaAgentRepository - Spring Data JPA repository for AgentEntity
 */
@Repository
public interface JpaAgentRepository extends JpaRepository<AgentEntity, String> {
    
    /**
     * Find agent by name
     */
    Optional<AgentEntity> findByName(String name);
    
    /**
     * Find all active agents
     */
    @Query("SELECT a FROM AgentEntity a WHERE a.status = 'ACTIVE'")
    List<AgentEntity> findAllActive();
    
    /**
     * Find agents owned by a specific user (paginated)
     */
    Page<AgentEntity> findByOwnerId(String ownerId, Pageable pageable);
    
    /**
     * Lightweight projection returning only id + symbol for active agents.
     */
    @Query("SELECT a.id as id, a.tradingSymbol as tradingSymbol FROM AgentEntity a WHERE a.status = 'ACTIVE'")
    List<ActiveAgentProjection> findAllActiveAgentSymbols();

    /**
     * Check if agent with name exists
     */
    boolean existsByName(String name);

    /**
     * Closed projection for active agent routing data.
     */
    interface ActiveAgentProjection {
        String getId();
        String getTradingSymbol();
    }
}
