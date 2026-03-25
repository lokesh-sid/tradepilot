package tradingbot.agent.infrastructure.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import tradingbot.agent.domain.model.Reasoning;
import tradingbot.agent.domain.model.ReasoningContext;

/**
 * CachedGrokService — A caching decorator for GrokClient.
 *
 * Activated ONLY when 'agent.llm.cache.enabled=true' (set this in backtest profile).
 * This prevents real Grok API calls during backtests, saving costs and enabling
 * fully reproducible, high-speed replay.
 *
 * Cache strategy (two-level):
 *   L1 — Redis: fast lookup, shared across JVM runs, configurable TTL.
 *   L2 — File system: survives Redis restarts, useful for offline backtests.
 *
 * Cache key: SHA-256( symbol + price + trend + sentiment + volume + goal )
 * The key is deterministic — same market conditions always yield the same cached response.
 * iterationCount and timestamp are intentionally excluded from the key.
 */
@Primary
@Component
@ConditionalOnProperty(name = "agent.llm.cache.enabled", havingValue = "true")
public class CachedGrokService implements LLMProvider {

    private static final Logger logger = LoggerFactory.getLogger(CachedGrokService.class);
    private static final String REDIS_KEY_PREFIX = "llm:cache:";

    private final GrokClient delegate;   // nullable — absent when grok is disabled (offline backtest)
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Path fileCacheDir;
    private final long redisTtlHours;

    public CachedGrokService(
            Optional<GrokClient> delegate,
            RedisTemplate<String, String> redisTemplate,
            @Value("${agent.llm.cache.file-dir:${java.io.tmpdir}/trading-bot-llm-cache}") String fileCacheDir,
            @Value("${agent.llm.cache.redis-ttl-hours:720}") long redisTtlHours) {
        this.delegate = delegate.orElse(null);
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.fileCacheDir = Paths.get(fileCacheDir);
        this.redisTtlHours = redisTtlHours;
        ensureFileCacheDir();
    }

    @Override
    public Reasoning generateReasoning(ReasoningContext context) {
        String cacheKey = buildCacheKey(context);

        // --- L1: Redis cache ---
        Reasoning fromRedis = readFromRedis(cacheKey);
        if (fromRedis != null) {
            logger.debug("[LLM Cache HIT - Redis] key={}", cacheKey);
            return fromRedis;
        }

        // --- L2: File cache ---
        Reasoning fromFile = readFromFile(cacheKey);
        if (fromFile != null) {
            logger.debug("[LLM Cache HIT - File] key={}", cacheKey);
            // Backfill Redis so subsequent calls are faster
            writeToRedis(cacheKey, fromFile);
            return fromFile;
        }

        // --- Cache MISS: call real Grok API (or offline synthetic fallback) ---
        Reasoning reasoning;
        if (delegate != null && delegate.isEnabled()) {
            logger.info("[LLM Cache MISS] Calling real Grok API. key={}, symbol={}, price={}",
                    cacheKey, context.getTradingSymbol(), context.getPerception().getCurrentPrice());
            reasoning = delegate.generateReasoning(context);
        } else {
            logger.info("[LLM Cache MISS - Offline] Generating synthetic reasoning. key={}, symbol={}, price={}",
                    cacheKey, context.getTradingSymbol(), context.getPerception().getCurrentPrice());
            reasoning = syntheticReasoning(context);
        }

        // Store in both caches
        writeToRedis(cacheKey, reasoning);
        writeToFile(cacheKey, reasoning);

        return reasoning;
    }

    @Override
    public boolean isEnabled() {
        return delegate == null || delegate.isEnabled();
    }

    @Override
    public String getProviderName() {
        return "CachedGrok (wraps: " + (delegate != null ? delegate.getProviderName() : "synthetic-offline") + ")";
    }

    // -------------------------------------------------------------------------
    // Offline Synthetic Reasoning (used when Grok API is disabled/unavailable)
    // -------------------------------------------------------------------------

    /**
     * Generates a deterministic synthetic Reasoning when no real LLM is available.
     * Logic: UPTREND → BUY (75%), DOWNTREND → SELL (70%), else HOLD (60%).
     * This populates the file cache on first run so subsequent backtest iterations
     * with the same market context are served instantly from L2 cache.
     */
    private Reasoning syntheticReasoning(ReasoningContext context) {
        String trend = context.getPerception().getTrend();
        String symbol = context.getTradingSymbol();
        double price = context.getPerception().getCurrentPrice();

        String recommendation;
        int confidence;
        String analysis;

        if ("UPTREND".equalsIgnoreCase(trend) || "BULLISH".equalsIgnoreCase(trend)) {
            recommendation = "BUY";
            confidence = 75;
            analysis = "Synthetic analysis: %s showing %s at %.2f. Positive momentum detected.".formatted(symbol, trend, price);
        } else if ("DOWNTREND".equalsIgnoreCase(trend) || "BEARISH".equalsIgnoreCase(trend)) {
            recommendation = "SELL";
            confidence = 70;
            analysis = "Synthetic analysis: %s showing %s at %.2f. Negative momentum detected.".formatted(symbol, trend, price);
        } else {
            recommendation = "HOLD";
            confidence = 60;
            analysis = "Synthetic analysis: %s showing neutral/sideways at %.2f. No clear signal.".formatted(symbol, price);
        }

        return new Reasoning(
                "[Synthetic] Price=%.2f, Trend=%s, Sentiment=%s".formatted(
                        price, trend, context.getPerception().getSentiment()),
                analysis,
                "Risk: standard backtest risk — no real capital at stake.",
                recommendation,
                confidence,
                Instant.now()
        );
    }

    // -------------------------------------------------------------------------
    // Cache Key
    // -------------------------------------------------------------------------

    /**
     * Build a deterministic SHA-256 cache key from the market context fields.
     * Excludes iterationCount and timestamp — same market state = same cache key.
     */
    private String buildCacheKey(ReasoningContext context) {
        String raw = String.join("|",
                context.getTradingSymbol(),
                String.valueOf(context.getPerception().getCurrentPrice()),
                String.valueOf(context.getPerception().getVolume()),
                nullSafe(context.getPerception().getTrend()),
                nullSafe(context.getPerception().getSentiment()),
                context.getGoal() != null ? context.getGoal().toString() : "null"
        );
        return sha256(raw);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be present in all JVMs
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    // -------------------------------------------------------------------------
    // Redis (L1)
    // -------------------------------------------------------------------------

    private Reasoning readFromRedis(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + cacheKey);
            if (json != null) {
                return fromDto(objectMapper.readValue(json, CachedReasoningDto.class));
            }
        } catch (Exception e) {
            logger.warn("[LLM Cache] Redis read failed (will fall back to file): {}", e.getMessage());
        }
        return null;
    }

    private void writeToRedis(String cacheKey, Reasoning reasoning) {
        try {
            String json = objectMapper.writeValueAsString(toDto(reasoning));
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + cacheKey, json, redisTtlHours, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.warn("[LLM Cache] Redis write failed (non-fatal): {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // File Cache (L2)
    // -------------------------------------------------------------------------

    private Reasoning readFromFile(String cacheKey) {
        Path file = fileCacheDir.resolve(cacheKey + ".json");
        if (!Files.exists(file)) return null;
        try {
            String json = Files.readString(file);
            return fromDto(objectMapper.readValue(json, CachedReasoningDto.class));
        } catch (IOException e) {
            logger.warn("[LLM Cache] File read failed for key={}: {}", cacheKey, e.getMessage());
            return null;
        }
    }

    private void writeToFile(String cacheKey, Reasoning reasoning) {
        Path file = fileCacheDir.resolve(cacheKey + ".json");
        try {
            String json = objectMapper.writeValueAsString(toDto(reasoning));
            Files.writeString(file, json);
        } catch (IOException e) {
            logger.warn("[LLM Cache] File write failed (non-fatal): {}", e.getMessage());
        }
    }

    private void ensureFileCacheDir() {
        try {
            Files.createDirectories(fileCacheDir);
            logger.info("[LLM Cache] File cache directory: {}", fileCacheDir.toAbsolutePath());
        } catch (IOException e) {
            logger.warn("[LLM Cache] Could not create file cache directory {}: {}", fileCacheDir, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // DTO for serialization (Reasoning has no no-arg constructor)
    // -------------------------------------------------------------------------

    private CachedReasoningDto toDto(Reasoning r) {
        return new CachedReasoningDto(
                r.getObservation(),
                r.getAnalysis(),
                r.getRiskAssessment(),
                r.getRecommendation(),
                r.getConfidence(),
                r.getTimestamp()
        );
    }

    private Reasoning fromDto(CachedReasoningDto dto) {
        return new Reasoning(
                dto.observation(),
                dto.analysis(),
                dto.riskAssessment(),
                dto.recommendation(),
                dto.confidence(),
                dto.timestamp()
        );
    }

    /**
     * Internal DTO used only for JSON serialization/deserialization of cached responses.
     */
    record CachedReasoningDto(
            @JsonProperty("observation") String observation,
            @JsonProperty("analysis") String analysis,
            @JsonProperty("riskAssessment") String riskAssessment,
            @JsonProperty("recommendation") String recommendation,
            @JsonProperty("confidence") int confidence,
            @JsonProperty("timestamp") Instant timestamp
    ) {
        @JsonCreator
        CachedReasoningDto {}
    }
}
