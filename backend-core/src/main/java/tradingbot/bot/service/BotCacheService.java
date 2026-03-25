package tradingbot.bot.service;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import tradingbot.bot.controller.dto.BotState;

/**
 * Service for caching bot state in Redis
 * 
 * Provides persistence, recovery, and horizontal scaling capabilities
 * by storing bot state in Redis.
 */
@Service
public class BotCacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(BotCacheService.class);
    private static final String BOT_STATE_PREFIX = "bot:state:";
    private static final long DEFAULT_TTL_DAYS = 7;
    
    private final RedisTemplate<String, BotState> botStateRedisTemplate;
    
    public BotCacheService(RedisTemplate<String, BotState> botStateRedisTemplate) {
        this.botStateRedisTemplate = botStateRedisTemplate;
    }
    
    /**
     * Save bot state to Redis with default TTL
     */
    public void saveBotState(String botId, BotState state) {
        saveBotState(botId, state, DEFAULT_TTL_DAYS, TimeUnit.DAYS);
    }
    
    /**
     * Save bot state to Redis with custom TTL
     */
    public void saveBotState(String botId, BotState state, long timeout, TimeUnit unit) {
        try {
            String key = getKey(botId);
            state.setLastUpdated(Instant.now());
            botStateRedisTemplate.opsForValue().set(key, state, timeout, unit);
            logger.debug("Saved bot state to Redis: {}", botId);
        } catch (Exception e) {
            logger.error("Failed to save bot state to Redis: {}", botId, e);
        }
    }
    
    /**
     * Get bot state from Redis
     */
    public BotState getBotState(String botId) {
        try {
            String key = getKey(botId);
            BotState state = botStateRedisTemplate.opsForValue().get(key);
            if (state != null) {
                logger.debug("Retrieved bot state from Redis: {}", botId);
            }
            return state;
        } catch (Exception e) {
            logger.error("Failed to get bot state from Redis: {}", botId, e);
            return null;
        }
    }
    
    /**
     * Delete bot state from Redis
     */
    public void deleteBotState(String botId) {
        try {
            String key = getKey(botId);
            Boolean deleted = botStateRedisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                logger.debug("Deleted bot state from Redis: {}", botId);
            }
        } catch (Exception e) {
            logger.error("Failed to delete bot state from Redis: {}", botId, e);
        }
    }
    
    /**
     * Check if bot state exists in Redis
     */
    public boolean exists(String botId) {
        try {
            String key = getKey(botId);
            return Boolean.TRUE.equals(botStateRedisTemplate.hasKey(key));
        } catch (Exception e) {
            logger.error("Failed to check bot state existence in Redis: {}", botId, e);
            return false;
        }
    }
    
    /**
     * Get all bot IDs from Redis
     */
    public Set<String> getAllBotIds() {
        try {
            Set<String> keys = botStateRedisTemplate.keys(BOT_STATE_PREFIX + "*");
            if (keys == null) {
                return Set.of();
            }
            return keys.stream()
                .map(key -> key.replace(BOT_STATE_PREFIX, ""))
                .collect(Collectors.toSet());
        } catch (Exception e) {
            logger.error("Failed to get all bot IDs from Redis", e);
            return Set.of();
        }
    }
    
    /**
     * Get all bot states from Redis
     */
    public Set<BotState> getAllBotStates() {
        try {
            return getAllBotIds().stream()
                .map(this::getBotState)
                .filter(state -> state != null)
                .collect(Collectors.toSet());
        } catch (Exception e) {
            logger.error("Failed to get all bot states from Redis", e);
            return Set.of();
        }
    }
    
    /**
     * Get all running bot states
     */
    public Set<BotState> getAllRunningBotStates() {
        return getAllBotStates().stream()
            .filter(BotState::isRunning)
            .collect(Collectors.toSet());
    }
    
    /**
     * Update bot running status
     */
    public void updateBotRunningStatus(String botId, boolean running) {
        BotState state = getBotState(botId);
        if (state != null) {
            state.setRunning(running);
            saveBotState(botId, state);
        }
    }
    
    /**
     * Update bot configuration
     */
    public void updateBotConfig(String botId, tradingbot.config.TradingConfig config) {
        BotState state = getBotState(botId);
        if (state != null) {
            state.setConfig(config);
            saveBotState(botId, state);
        }
    }
    
    /**
     * Clean up old inactive bot states
     */
    public int cleanupInactiveBots(long inactiveDays) {
        int count = 0;
        Instant threshold = Instant.now().minusSeconds(inactiveDays * 24 * 60 * 60);
        
        for (String botId : getAllBotIds()) {
            BotState state = getBotState(botId);
            if (state != null && !state.isRunning()) {
                Instant lastUpdated = state.getLastUpdated() != null ? 
                    state.getLastUpdated() : state.getCreatedAt();
                
                if (lastUpdated != null && lastUpdated.isBefore(threshold)) {
                    deleteBotState(botId);
                    count++;
                    logger.info("Cleaned up inactive bot: {} (last updated: {})", botId, lastUpdated);
                }
            }
        }
        
        if (count > 0) {
            logger.info("Cleaned up {} inactive bot states", count);
        }
        return count;
    }
    
    /**
     * Get Redis key for bot state
     */
    private String getKey(String botId) {
        return BOT_STATE_PREFIX + botId;
    }
}
