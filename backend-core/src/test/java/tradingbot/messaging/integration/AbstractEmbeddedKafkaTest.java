package tradingbot.messaging.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import tradingbot.bot.messaging.EventPublisher;

/**
 * Abstract base class for Kafka integration tests using embedded Kafka.
 *
 * This class provides common Kafka infrastructure setup that can be extended
 * by specific integration tests for different components (publishers, consumers, etc.).
 *
 * Usage:
 * ```java
 * @SpringJUnitConfig
 * @EmbeddedKafka(...) // Configure topics as needed
 * public class MyKafkaIntegrationTest extends AbstractEmbeddedKafkaTest {
 *     // Test implementation
 * }
 * ```
 */
@Configuration
@EmbeddedKafka(
    partitions = 1,
    controlledShutdown = true,
    topics = {
        "trading.signals",
        "trading.executions",
        "trading.risk",
        "trading.market-data",
        "trading.bot-status",
        "example.topic"
    },
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:0",
        "port=0"
    }
)
@TestPropertySource(
    properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=test-group-${random.uuid}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=*",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.producer.client-id=test-producer-${random.uuid}"
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractEmbeddedKafkaTest {

    @Autowired
    protected EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    protected KafkaTemplate<String, Object> kafkaTemplate;

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(EmbeddedKafkaBroker embeddedKafka) {
        return createKafkaTemplate(embeddedKafka);
    }

    /**
     * Provides an EventPublisher bean for testing.
     * This can be overridden in subclasses if custom configuration is needed.
     *
     * @param kafkaTemplate The KafkaTemplate to use
     * @return Configured EventPublisher
     */
    @Bean
    public EventPublisher eventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        return new EventPublisher(kafkaTemplate);
    }

    /**
     * Creates a KafkaTemplate configured for testing.
     * Override this method in subclasses if you need custom producer configuration.
     *
     * @param embeddedKafka The embedded Kafka broker
     * @return Configured KafkaTemplate
     */
    protected KafkaTemplate<String, Object> createKafkaTemplate(EmbeddedKafkaBroker embeddedKafka) {
        var producerProps = KafkaTestUtils.producerProps(embeddedKafka);
        producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("value.serializer", "org.springframework.kafka.support.serializer.JsonSerializer");

        return new KafkaTemplate<>(
            new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(producerProps)
        );
    }

    /**
     * Utility method to wait for Kafka operations to complete.
     * Useful for ensuring messages are processed before assertions.
     *
     * @param timeoutMs Timeout in milliseconds
     */
    protected void waitForKafkaOperations(long timeoutMs) {
        // Override in subclasses to implement custom waiting logic if needed
    }

    /**
     * Gets the bootstrap servers for the embedded Kafka.
     * Useful for custom Kafka client configurations.
     *
     * @return Bootstrap servers string
     */
    protected String getBootstrapServers() {
        return embeddedKafka.getBrokersAsString();
    }
}