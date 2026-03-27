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
    List<OrderEntity> findByExecutorId(String executorId);
    
    /**
     * Find all orders for a specific symbol
     */
    List<OrderEntity> findBySymbol(String symbol);
    
    /**
     * Find all orders for an agent on a specific symbol
     */
    List<OrderEntity> findByExecutorIdAndSymbol(String executorId, String symbol);
    
    /**
     * Find all orders with a specific status
     */
    List<OrderEntity> findByStatus(OrderEntity.Status status);
    
    /**
     * Find all orders for an agent with a specific status
     */
    List<OrderEntity> findByExecutorIdAndStatus(String executorId, OrderEntity.Status status);
    
    /**
     * Find orders created after a specific timestamp
     */
    List<OrderEntity> findByCreatedAtAfter(Instant timestamp);
    
    /**
     * Find recent orders for an agent (last N days)
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.executorId = :executorId " +
           "AND o.createdAt >= :since ORDER BY o.createdAt DESC")
    List<OrderEntity> findRecentOrdersByAgent(
        @Param("executorId") String executorId,
        @Param("since") Instant since
    );
    
    /**
     * Count orders by status for an agent
     */
    long countByExecutorIdAndStatus(String executorId, OrderEntity.Status status);
    
    /**
     * Find all executed orders for an agent
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.executorId = :executorId " +
           "AND o.status = 'EXECUTED' ORDER BY o.executedAt DESC")
    List<OrderEntity> findExecutedOrdersByAgent(@Param("executorId") String executorId);
}
