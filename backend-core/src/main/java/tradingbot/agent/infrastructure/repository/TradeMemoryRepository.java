package tradingbot.agent.infrastructure.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tradingbot.agent.infrastructure.persistence.TradeMemoryEntity;

/**
 * TradeMemoryRepository - JPA repository for TradeMemory metadata persistence
 *
 * Note: This stores metadata only. Embedding vectors are stored in Pinecone.
 * This repository is used for:
 * - Structured queries on trade outcomes
 * - Performance analytics
 * - Audit trails
 * - Backup of experience metadata
 */
@Repository
public interface TradeMemoryRepository extends JpaRepository<TradeMemoryEntity, String> {

    /**
     * Find all experiences for a specific agent
     */
    List<TradeMemoryEntity> findByAgentId(String agentId);

    /**
     * Find all experiences for a specific symbol
     */
    List<TradeMemoryEntity> findBySymbol(String symbol);

    /**
     * Find all experiences for an agent on a specific symbol
     */
    List<TradeMemoryEntity> findByAgentIdAndSymbol(String agentId, String symbol);

    /**
     * Find all experiences with a specific outcome
     */
    List<TradeMemoryEntity> findByOutcome(TradeMemoryEntity.Outcome outcome);

    /**
     * Find profitable trades for an agent
     */
        @Query("SELECT m FROM TradeMemoryEntity m WHERE m.agentId = :agentId " +
           "AND m.outcome = 'PROFIT' AND m.profitPercent > 0 " +
           "ORDER BY m.profitPercent DESC")
    List<TradeMemoryEntity> findProfitableTradesByAgent(@Param("agentId") String agentId);

    /**
     * Find experiences created after a specific timestamp
     */
    List<TradeMemoryEntity> findByTimestampAfter(Instant timestamp);

    /**
     * Find recent experiences for an agent (last N days)
     */
        @Query("SELECT m FROM TradeMemoryEntity m WHERE m.agentId = :agentId " +
           "ORDER BY m.timestamp DESC")
    List<TradeMemoryEntity> findRecentExperiencesByAgent(
        @Param("agentId") String agentId
    );

    /**
     * Calculate win rate for an agent
     */
    @Query("SELECT COUNT(m) * 100.0 / (SELECT COUNT(m2) FROM TradeMemoryEntity m2 " +
           "WHERE m2.agentId = :agentId AND m2.outcome IN ('PROFIT', 'LOSS', 'BREAKEVEN')) " +
           "FROM TradeMemoryEntity m WHERE m.agentId = :agentId AND m.outcome = 'PROFIT'")
    Double calculateWinRateByAgent(@Param("agentId") String agentId);

    /**
     * Calculate average profit/loss for an agent
     */
    @Query("SELECT AVG(m.profitPercent) FROM TradeMemoryEntity m " +
           "WHERE m.agentId = :agentId AND m.profitPercent IS NOT NULL")
    Double calculateAverageProfitByAgent(@Param("agentId") String agentId);

    /**
     * Count experiences by outcome for an agent
     */
    long countByAgentIdAndOutcome(String agentId, TradeMemoryEntity.Outcome outcome);

    /**
     * Find all PENDING memories for an agent on a specific symbol.
     * Used by the reflection pipeline to locate the pre-trade record that
     * should be updated once the real trade outcome is known.
     */
    @Query("SELECT m FROM TradeMemoryEntity m WHERE m.agentId = :agentId " +
           "AND m.symbol = :symbol AND m.outcome = 'PENDING' " +
           "ORDER BY m.timestamp DESC")
    List<TradeMemoryEntity> findPendingByAgentIdAndSymbol(
        @Param("agentId") String agentId,
        @Param("symbol") String symbol
    );
}