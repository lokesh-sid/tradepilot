package tradingbot.agent.infrastructure.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tradingbot.agent.infrastructure.persistence.PositionEntity;

/**
 * PositionRepository - JPA repository for Position persistence
 */
@Repository
public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

    /**
     * Find all positions for a specific agent
     */
    List<PositionEntity> findByAgentId(Long agentId);

    /**
     * Find all positions with a specific status
     */
    List<PositionEntity> findByStatus(PositionEntity.Status status);

    /**
     * Find all open positions for a specific agent
     */
    List<PositionEntity> findByAgentIdAndStatus(Long agentId, PositionEntity.Status status);
    
    /**
     * Find all open positions for a specific symbol
     */
    List<PositionEntity> findBySymbolAndStatus(String symbol, PositionEntity.Status status);
    
    /**
     * Find all positions for a specific symbol
     */
    List<PositionEntity> findBySymbol(String symbol);
}
