package tradingbot;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class GenericContainerSmokeTest {
    @Test
    void testContainerStartup() {
        try (GenericContainer<?> container = new GenericContainer<>(
                DockerImageName.parse("alpine:3.18")
        ).withCommand("sleep", "5")) {
            container.start();
            assertTrue(container.isRunning(), "Container should be running");
        }
    }
}
