package tradingbot.bot.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tradingbot.bot.persistence.entity.TradingEventEntity;

/**
 * Repository for trading events persistence.
 * Provides CRUD operations and custom queries for event analytics.
 */
@Repository
public interface TradingEventRepository extends JpaRepository<TradingEventEntity, String> {
    
    /**
     * Find event by event ID.
     */
    Optional<TradingEventEntity> findByEventId(String eventId);
    
    /**
     * Find all events for a specific bot.
     */
    Page<TradingEventEntity> findByBotId(String botId, Pageable pageable);
    
    /**
     * Find all events by type.
     */
    Page<TradingEventEntity> findByEventType(String eventType, Pageable pageable);
    
    /**
     * Find all events for a specific symbol.
     */
    Page<TradingEventEntity> findBySymbol(String symbol, Pageable pageable);
    
    /**
     * Find all events for a bot within a time range.
     */
    @Query("SELECT e FROM TradingEventEntity e WHERE e.botId = :botId " +
           "AND e.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY e.timestamp DESC")
    List<TradingEventEntity> findByBotIdAndTimeRange(
        @Param("botId") String botId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Find all events by type and bot within a time range.
     */
    @Query("SELECT e FROM TradingEventEntity e WHERE e.botId = :botId " +
           "AND e.eventType = :eventType " +
           "AND e.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY e.timestamp DESC")
    List<TradingEventEntity> findByBotIdAndEventTypeAndTimeRange(
        @Param("botId") String botId,
        @Param("eventType") String eventType,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Count events by type for a specific bot.
     */
    @Query("SELECT e.eventType, COUNT(e) FROM TradingEventEntity e " +
           "WHERE e.botId = :botId GROUP BY e.eventType")
    List<Object[]> countEventsByType(@Param("botId") String botId);
    
    /**
     * Find recent events for a bot.
     */
    @Query("SELECT e FROM TradingEventEntity e WHERE e.botId = :botId " +
           "ORDER BY e.timestamp DESC")
    Page<TradingEventEntity> findRecentEventsByBotId(@Param("botId") String botId, Pageable pageable);
    
    /**
     * Delete old events before a given date.
     */
    void deleteByTimestampBefore(LocalDateTime timestamp);
}
