package tradingbot.bot.grpc;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Minimal Spring Boot bootstrap for gRPC integration tests.
 *
 * <p>Lives in the same package as the test so that {@code @SpringBootTest}
 * finds this {@code @SpringBootConfiguration} before walking up the hierarchy
 * to {@code AgenticTradingApplication}. This prevents the full application
 * context (JPA, Kafka, etc.) from being loaded.
 *
 * <p>Auto-configuration is enabled so the gRPC and Redis starters initialise
 * themselves, but JPA, Hibernate, and Kafka are explicitly excluded.
 * The beans needed by the tests (BotManagementServiceImpl, BotCacheService,
 * RedisTemplate) are wired in via {@link GrpcTestConfiguration}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
    HibernateJpaAutoConfiguration.class,
    DataSourceAutoConfiguration.class,
    KafkaAutoConfiguration.class
})
@Import(GrpcTestConfiguration.class)
class GrpcTestBootstrap {
}
