package tradingbot.bot.service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tradingbot.bot.controller.dto.BotState;

/**
 * In-memory implementation of BotCacheService for testing.
 * Provides the same interface as Redis-based implementation but stores data in memory.
 */
public class InMemoryBotCacheService extends BotCacheService {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryBotCacheService.class);

    private final Map<String, BotState> botStates = new ConcurrentHashMap<>();

    // Constructor that doesn't require Redis
    public InMemoryBotCacheService() {
        super(null); // Pass null for Redis template
    }

    @Override
    public void saveBotState(String botId, BotState state) {
        state.setLastUpdated(Instant.now());
        botStates.put(botId, state);
        logger.debug("Saved bot state in memory: {}", botId);
    }

    @Override
    public void saveBotState(String botId, BotState state, long timeout, java.util.concurrent.TimeUnit unit) {
        saveBotState(botId, state); // Ignore TTL for in-memory implementation
    }

    @Override
    public BotState getBotState(String botId) {
        BotState state = botStates.get(botId);
        if (state != null) {
            logger.debug("Retrieved bot state from memory: {}", botId);
        }
        return state;
    }

    @Override
    public void deleteBotState(String botId) {
        BotState removed = botStates.remove(botId);
        if (removed != null) {
            logger.debug("Deleted bot state from memory: {}", botId);
        }
    }

    @Override
    public boolean exists(String botId) {
        return botStates.containsKey(botId);
    }

    @Override
    public Set<String> getAllBotIds() {
        return botStates.keySet();
    }

    @Override
    public Set<BotState> getAllBotStates() {
        return Set.copyOf(botStates.values());
    }

    @Override
    public Set<BotState> getAllRunningBotStates() {
        return botStates.values().stream()
            .filter(BotState::isRunning)
            .collect(Collectors.toSet());
    }

    @Override
    public void updateBotRunningStatus(String botId, boolean running) {
        BotState state = getBotState(botId);
        if (state != null) {
            state.setRunning(running);
            saveBotState(botId, state);
        }
    }

    @Override
    public void updateBotConfig(String botId, tradingbot.config.TradingConfig config) {
        BotState state = getBotState(botId);
        if (state != null) {
            state.setConfig(config);
            saveBotState(botId, state);
        }
    }

    @Override
    public int cleanupInactiveBots(long inactiveDays) {
        Instant threshold = Instant.now().minusSeconds(inactiveDays * 24 * 60 * 60);
        int count = 0;

        for (Map.Entry<String, BotState> entry : botStates.entrySet()) {
            BotState state = entry.getValue();
            if (!state.isRunning()) {
                Instant lastUpdated = state.getLastUpdated() != null ?
                    state.getLastUpdated() : state.getCreatedAt();

                if (lastUpdated != null && lastUpdated.isBefore(threshold)) {
                    botStates.remove(entry.getKey());
                    count++;
                    logger.info("Cleaned up inactive bot: {} (last updated: {})", entry.getKey(), lastUpdated);
                }
            }
        }

        if (count > 0) {
            logger.info("Cleaned up {} inactive bot states", count);
        }
        return count;
    }
}