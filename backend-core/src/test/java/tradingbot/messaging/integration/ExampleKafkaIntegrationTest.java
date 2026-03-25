package tradingbot.messaging.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Example integration test showing how to extend AbstractEmbeddedKafkaTest
 * for testing other Kafka-based components.
 *
 * This demonstrates the reusability of the abstract base class.
 */
@SpringJUnitConfig(classes = AbstractEmbeddedKafkaTest.class)
@DisplayName("Example Kafka Integration Test")
class ExampleKafkaIntegrationTest extends AbstractEmbeddedKafkaTest {

    @Test
    @DisplayName("Should demonstrate abstract class usage")
    void shouldDemonstrateAbstractClassUsage() {
        // Access protected fields from abstract class
        assertNotNull(embeddedKafka, "EmbeddedKafka should be available");
        assertNotNull(kafkaTemplate, "KafkaTemplate should be available");

        // Use utility methods
        String bootstrapServers = getBootstrapServers();
        assertNotNull(bootstrapServers, "Bootstrap servers should be available");
        assertTrue(bootstrapServers.contains("127.0.0.1") || bootstrapServers.contains("localhost"), "Should contain localhost or 127.0.0.1");

        // Example: Send a test message
        kafkaTemplate.send("example.topic", "test-key", "test-message");

        // Wait for async operations (customize timeout as needed)
        waitForKafkaOperations(100);
    }
}