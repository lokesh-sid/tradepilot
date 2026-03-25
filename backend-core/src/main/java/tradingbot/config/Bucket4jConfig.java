package tradingbot.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

/**
 * Bucket4j configuration for distributed inbound rate limiting.
 *
 * <p>Provides a Redis-backed {@link ProxyManager} and a shared {@link BucketConfiguration}
 * used by {@link tradingbot.security.filter.AuthRateLimitFilter} to enforce per-IP request
 * quotas cluster-wide.
 *
 * <p>Assumes a standalone Redis instance (Lettuce, not cluster).
 */
@Configuration
@EnableConfigurationProperties(Bucket4jConfig.AuthRateLimitProperties.class)
public class Bucket4jConfig {

    /**
     * Binds {@code auth.rate-limit.*} properties.
     * Defaults match the previous hardcoded values: 20 requests per 60 seconds.
     */
    @ConfigurationProperties(prefix = "auth.rate-limit")
    public static class AuthRateLimitProperties {
        private int capacity = 20;
        private int refillPeriodSeconds = 60;

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }

        public int getRefillPeriodSeconds() { return refillPeriodSeconds; }
        public void setRefillPeriodSeconds(int refillPeriodSeconds) { this.refillPeriodSeconds = refillPeriodSeconds; }
    }

    @Bean
    BucketConfiguration authRateLimitBucketConfiguration(AuthRateLimitProperties props) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(props.getCapacity())
                        .refillGreedy(props.getCapacity(), Duration.ofSeconds(props.getRefillPeriodSeconds()))
                        .build())
                .build();
    }

    @Bean
    ProxyManager<String> authRateLimitProxyManager(RedisConnectionFactory redisConnectionFactory,
                                                   AuthRateLimitProperties props) {
        RedisClient redisClient = (RedisClient) ((LettuceConnectionFactory) redisConnectionFactory).getNativeClient();
        var connection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        Duration ttl = Duration.ofSeconds(props.getRefillPeriodSeconds() * 2L);
        ClientSideConfig clientSideConfig = ClientSideConfig.getDefault()
                .withExpirationAfterWriteStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(ttl));
        return LettuceBasedProxyManager.builderFor(connection)
                .withClientSideConfig(clientSideConfig)
                .build();
    }
}
