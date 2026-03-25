package tradingbot.agent.infrastructure.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tradingbot.agent.infrastructure.persistence.OrderEntity;

/**
 * OrderRepository - JPA repository for Order persistence
 */
@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {
    
    /**
     * Find all orders for a specific agent
     */
    List<OrderEntity> findByAgentId(String agentId);
    
    /**
     * Find all orders for a specific symbol
     */
    List<OrderEntity> findBySymbol(String symbol);
    
    /**
     * Find all orders for an agent on a specific symbol
     */
    List<OrderEntity> findByAgentIdAndSymbol(String agentId, String symbol);
    
    /**
     * Find all orders with a specific status
     */
    List<OrderEntity> findByStatus(OrderEntity.Status status);
    
    /**
     * Find all orders for an agent with a specific status
     */
    List<OrderEntity> findByAgentIdAndStatus(String agentId, OrderEntity.Status status);
    
    /**
     * Find orders created after a specific timestamp
     */
    List<OrderEntity> findByCreatedAtAfter(Instant timestamp);
    
    /**
     * Find recent orders for an agent (last N days)
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.agentId = :agentId " +
           "AND o.createdAt >= :since ORDER BY o.createdAt DESC")
    List<OrderEntity> findRecentOrdersByAgent(
        @Param("agentId") String agentId,
        @Param("since") Instant since
    );
    
    /**
     * Count orders by status for an agent
     */
    long countByAgentIdAndStatus(String agentId, OrderEntity.Status status);
    
    /**
     * Find all executed orders for an agent
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.agentId = :agentId " +
           "AND o.status = 'EXECUTED' ORDER BY o.executedAt DESC")
    List<OrderEntity> findExecutedOrdersByAgent(@Param("agentId") String agentId);
}
