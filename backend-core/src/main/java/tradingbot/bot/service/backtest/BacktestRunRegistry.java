package tradingbot.bot.service.backtest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import tradingbot.bot.service.backtest.BacktestMetricsCalculator.BacktestMetrics;

/**
 * BacktestRunRegistry — in-memory store for completed backtest runs.
 *
 * <p>Maps {@code runId → BacktestMetrics} so that a single POST to
 * {@code /api/v1/backtest} creates a run that can later be retrieved
 * via GET endpoints without re-executing the backtest.
 *
 * <p>No persistence is required: backtests are fast, and results are
 * intentionally ephemeral (process restart clears old runs). Phase 5
 * can add JPA persistence if needed.
 *
 * <h3>Thread safety</h3>
 * Uses {@link ConcurrentHashMap} — safe for concurrent requests from
 * multiple HTTP threads without external synchronisation.
 */
@Component
public class BacktestRunRegistry {

    private final ConcurrentHashMap<String, BacktestMetrics> store = new ConcurrentHashMap<>();

    /**
     * Stores a completed run. The {@code runId} is taken from
     * {@link BacktestMetrics#runId()}.
     */
    public void save(BacktestMetrics metrics) {
        store.put(metrics.runId(), metrics);
    }

    /**
     * Retrieves a single run by its ID.
     *
     * @return {@code Optional.empty()} when the ID is not found (e.g., after restart)
     */
    public Optional<BacktestMetrics> find(String runId) {
        return Optional.ofNullable(store.get(runId));
    }

    /**
     * Returns all stored runs. Order is not guaranteed.
     */
    public List<BacktestMetrics> findAll() {
        return List.copyOf(store.values());
    }

    /**
     * Deletes a single run.
     *
     * @return {@code true} if the run existed and was removed
     */
    public boolean delete(String runId) {
        return store.remove(runId) != null;
    }

    /** Returns the number of stored runs. */
    public int size() {
        return store.size();
    }
}
