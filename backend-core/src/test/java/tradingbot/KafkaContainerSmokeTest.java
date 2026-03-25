package tradingbot;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class KafkaContainerSmokeTest {
        @Container
        static final ConfluentKafkaContainer kafka =
                new ConfluentKafkaContainer(
                    DockerImageName.parse("confluentinc/cp-kafka:7.7.0")
                );

    @Test
    void kafkaShouldStart() {
        assertTrue(kafka.isRunning(), "Kafka container should be running");
    }
}
