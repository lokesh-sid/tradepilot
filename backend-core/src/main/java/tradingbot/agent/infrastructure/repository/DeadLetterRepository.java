package tradingbot.agent.infrastructure.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tradingbot.agent.infrastructure.persistence.DeadLetterEntity;

/**
 * DeadLetterRepository — persists and queries failed Kafka events that were routed
 * to a Dead Letter Topic (.DLT).
 *
 * Usage:
 *  - DeadLetterConsumer calls save() for every DLT event received.
 *  - Ops tooling queries countRecentByTopic() for threshold-based alerting.
 *  - Ops tooling queries findUnresolvedByTopic() to review / replay failures.
 */
@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterEntity, String> {

    /**
     * All unresolved failures for a given original topic (e.g. "kline-closed.BTCUSDT"),
     * ordered newest first. Used by ops tooling to list actionable failures.
     */
    List<DeadLetterEntity> findByOriginalTopicAndResolvedFalseOrderByReceivedAtDesc(String originalTopic);

    /**
     * Count of unresolved failures for a topic within a sliding time window.
     * Used by the threshold alerting logic in DeadLetterConsumer.
     *
     * @param originalTopic the original (non-DLT) topic name
     * @param since         start of the sliding window (e.g. now minus 5 minutes)
     * @return number of unresolved failures in the window
     */
    @Query("""
        SELECT COUNT(e) FROM DeadLetterEntity e
        WHERE e.originalTopic = :topic
          AND e.resolved = false
          AND e.receivedAt >= :since
        """)
    long countRecentUnresolvedByTopic(@Param("topic") String originalTopic,
                                      @Param("since") Instant since);

    /**
     * All unresolved failures across every topic, ordered by received time descending.
     * Useful for an ops dashboard or health endpoint.
     */
    List<DeadLetterEntity> findByResolvedFalseOrderByReceivedAtDesc();

    /**
     * Failures for a given exception type — useful for identifying systemic issues
     * (e.g. all NullPointerExceptions across all topics).
     */
    List<DeadLetterEntity> findByExceptionTypeAndResolvedFalseOrderByReceivedAtDesc(String exceptionType);
}
