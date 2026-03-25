package tradingbot.bot.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tradingbot.bot.persistence.entity.TradeExecutionEventEntity;

/**
 * Repository for trade execution events.
 * Provides specialized queries for trade history and analytics.
 */
@Repository
public interface TradeExecutionRepository extends JpaRepository<TradeExecutionEventEntity, String> {
    
    /**
     * Find all trades for a specific bot.
     */
    Page<TradeExecutionEventEntity> findByBotId(String botId, Pageable pageable);
    
    /**
     * Find all trades for a specific symbol.
     */
    Page<TradeExecutionEventEntity> findBySymbol(String symbol, Pageable pageable);
    
    /**
     * Find all trades by status.
     */
    List<TradeExecutionEventEntity> findByStatus(String status);
    
    /**
     * Calculate total profit/loss for a bot.
     */
    @Query("SELECT COALESCE(SUM(e.profitLoss), 0.0) FROM TradeExecutionEventEntity e " +
           "WHERE e.botId = :botId AND e.profitLoss IS NOT NULL")
    Double calculateTotalProfitLoss(@Param("botId") String botId);
    
    /**
     * Calculate total profit/loss for a bot within a time range.
     */
    @Query("SELECT COALESCE(SUM(e.profitLoss), 0.0) FROM TradeExecutionEventEntity e " +
           "WHERE e.botId = :botId AND e.profitLoss IS NOT NULL " +
           "AND e.timestamp BETWEEN :startTime AND :endTime")
    Double calculateProfitLossInTimeRange(
        @Param("botId") String botId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Count trades by side (BUY/SELL) for a bot.
     */
    @Query("SELECT e.side, COUNT(e) FROM TradeExecutionEventEntity e " +
           "WHERE e.botId = :botId GROUP BY e.side")
    List<Object[]> countTradesBySide(@Param("botId") String botId);
    
    /**
     * Find winning trades (positive profit/loss).
     */
    @Query("SELECT e FROM TradeExecutionEventEntity e WHERE e.botId = :botId " +
           "AND e.profitLoss > 0 ORDER BY e.profitLoss DESC")
    List<TradeExecutionEventEntity> findWinningTrades(@Param("botId") String botId);
    
    /**
     * Find losing trades (negative profit/loss).
     */
    @Query("SELECT e FROM TradeExecutionEventEntity e WHERE e.botId = :botId " +
           "AND e.profitLoss < 0 ORDER BY e.profitLoss ASC")
    List<TradeExecutionEventEntity> findLosingTrades(@Param("botId") String botId);
    
    /**
     * Calculate average trade profit/loss.
     */
    @Query("SELECT AVG(e.profitLoss) FROM TradeExecutionEventEntity e " +
           "WHERE e.botId = :botId AND e.profitLoss IS NOT NULL")
    Double calculateAverageProfitLoss(@Param("botId") String botId);
    
    /**
     * Get trade statistics for a symbol.
     */
    @Query("SELECT COUNT(e), SUM(e.quantity), AVG(e.price), " +
           "MIN(e.price), MAX(e.price) FROM TradeExecutionEventEntity e " +
           "WHERE e.symbol = :symbol")
    Object[] getTradeStatisticsBySymbol(@Param("symbol") String symbol);
}
