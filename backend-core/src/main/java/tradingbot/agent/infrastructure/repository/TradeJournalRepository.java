package tradingbot.agent.infrastructure.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tradingbot.agent.infrastructure.persistence.TradeJournalEntity;
import tradingbot.agent.infrastructure.persistence.TradeJournalEntity.Outcome;

@Repository
public interface TradeJournalRepository extends JpaRepository<TradeJournalEntity, Long> {

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /** Most recent PENDING entry for an agent+symbol — used to complete at close. */
    Optional<TradeJournalEntity> findTopByAgentIdAndSymbolAndOutcomeOrderByDecidedAtDesc(
            Long agentId, String symbol, Outcome outcome);

    // -------------------------------------------------------------------------
    // Filtered list queries (for JournalController)
    // -------------------------------------------------------------------------

    Page<TradeJournalEntity> findByAgentId(Long agentId, Pageable pageable);

    Page<TradeJournalEntity> findBySymbol(String symbol, Pageable pageable);

    Page<TradeJournalEntity> findByAgentIdAndSymbol(Long agentId, String symbol, Pageable pageable);

    Page<TradeJournalEntity> findByOutcome(Outcome outcome, Pageable pageable);

    Page<TradeJournalEntity> findByAgentIdAndOutcome(Long agentId, Outcome outcome, Pageable pageable);

    Page<TradeJournalEntity> findByFlaggedForReview(boolean flaggedForReview, Pageable pageable);

    Page<TradeJournalEntity> findByDecidedAtBetween(Instant from, Instant to, Pageable pageable);

    // -------------------------------------------------------------------------
    // Batch review queries (for LLMStrategyReviewService)
    // -------------------------------------------------------------------------

    /** Closed trades not yet batch-reviewed, within a lookback window. */
    @Query("""
        SELECT j FROM TradeJournalEntity j
        WHERE j.outcome <> :pending
          AND j.llmBatchAnalysis IS NULL
          AND j.closedAt >= :since
        ORDER BY j.closedAt DESC
        """)
    List<TradeJournalEntity> findUnreviewedClosed(
            @Param("since") Instant since,
            @Param("pending") Outcome pending);

    // -------------------------------------------------------------------------
    // Stats queries (for JournalController /stats)
    // -------------------------------------------------------------------------

    @Query("""
        SELECT COUNT(j) FROM TradeJournalEntity j
        WHERE j.agentId = :agentId AND j.outcome <> :pending
        """)
    long countClosed(@Param("agentId") Long agentId, @Param("pending") Outcome pending);

    @Query("""
        SELECT COUNT(j) FROM TradeJournalEntity j
        WHERE j.agentId = :agentId AND j.outcome = :profit
        """)
    long countWins(@Param("agentId") Long agentId, @Param("profit") Outcome profit);

    @Query("""
        SELECT AVG(j.pnlPercent) FROM TradeJournalEntity j
        WHERE j.agentId = :agentId AND j.outcome <> :pending AND j.pnlPercent IS NOT NULL
        """)
    Double avgPnlPercent(@Param("agentId") Long agentId, @Param("pending") Outcome pending);

    @Query("""
        SELECT AVG(j.confidence) FROM TradeJournalEntity j
        WHERE j.agentId = :agentId AND j.outcome = :profit AND j.confidence IS NOT NULL
        """)
    Double avgConfidenceOnWins(@Param("agentId") Long agentId, @Param("profit") Outcome profit);

    @Query("""
        SELECT AVG(j.confidence) FROM TradeJournalEntity j
        WHERE j.agentId = :agentId AND j.outcome = :loss AND j.confidence IS NOT NULL
        """)
    Double avgConfidenceOnLosses(@Param("agentId") Long agentId, @Param("loss") Outcome loss);

    /** All closed entries for an agent — used for tag-level stats in service layer. */
    @Query("""
        SELECT j FROM TradeJournalEntity j
        WHERE j.agentId = :agentId AND j.outcome <> :pending
        ORDER BY j.decidedAt DESC
        """)
    List<TradeJournalEntity> findAllClosedByAgent(
            @Param("agentId") Long agentId,
            @Param("pending") Outcome pending);
}
