package tradingbot;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Abstract base class for integration tests that rely on real infrastructure containers.
 *
 * <p>Starts a PostgreSQL 16 container and a Kafka (Confluent) container once per JVM
 * run and wires their connection details into the Spring application context via
 * {@link DynamicPropertySource} before the context is refreshed.
 *
 * <p>Containers are started in a <em>static initializer</em> (not via {@code @Container}
 * or {@code @BeforeAll}) for a specific reason: {@link DynamicPropertySource} suppliers
 * are evaluated inside {@code SpringExtension.beforeAll()}, which JUnit 5 invokes
 * <em>before</em> any {@code @BeforeAll} methods in the test class. This means that by
 * the time {@code @BeforeAll} would run, Spring has already tried to call
 * {@code postgres.getJdbcUrl()} — and would throw if the container isn't yet started.
 * A static initializer runs when the class is first referenced, guaranteeing containers
 * are ready before any JUnit lifecycle callback fires.
 *
 * <p>There is intentionally no {@code @Container} annotation. That annotation causes
 * the Testcontainers JUnit 5 extension to call {@code stop()} after each test class,
 * which would invalidate Spring's cached application contexts (they retain the old port
 * while the restarted container binds a new one).
 *
 * <p>Concrete subclasses need only to declare the {@code @SpringBootTest} annotation
 * (and any additional test-specific configuration) – the containers are managed here.
 */
public abstract class AbstractContainerIntegrationTest extends AbstractHttpTest {

    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
            .withDatabaseName("trading_bot")
            .withUsername("tradingbot")
            .withPassword("tradingbot123")
            .withEnv("POSTGRES_HOST_AUTH_METHOD", "trust");

    // KafkaContainer is deprecated in favour of ConfluentKafkaContainer, but
    // ConfluentKafkaContainer cannot be used here: it fails to start reliably inside
    // a static initializer (different wait strategy / startup timing). KafkaContainer
    // works correctly in this context; the deprecation is a warning only.
    @SuppressWarnings("deprecation")
    static final KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.0"));

    static {
        postgres.start();
        kafka.start();
    }

    /**
     * Injects container-specific connection properties into the Spring
     * {@link org.springframework.core.env.Environment} before the context is refreshed.
     */
    @DynamicPropertySource
    static void configureContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
