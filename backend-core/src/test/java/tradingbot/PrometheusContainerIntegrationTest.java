package tradingbot;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import tradingbot.config.ContainerIntegrationTestConfig;

/**
 * End-to-end integration test that validates the application against real
 * infrastructure (PostgreSQL + Kafka) managed by Testcontainers.
 *
 * <h2>What is verified</h2>
 * <ul>
 *   <li>Both containers start successfully and are reachable.</li>
 *   <li>The Spring application context loads with a real PostgreSQL datasource.</li>
 *   <li>{@code /actuator/health} returns {@code 200 OK} with status {@code UP}.</li>
 *   <li>{@code /actuator/metrics} exposes standard JVM metrics and the custom
 *       {@code trading.*} gauges registered by {@link tradingbot.bot.metrics.TradingMetrics}.</li>
 * </ul>
 */
@SpringBootTest(
    classes = ContainerIntegrationTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("container-test")
@Tag("requires-docker")
@DisplayName("Testcontainers + Micrometer E2E Integration Test")
class PrometheusContainerIntegrationTest extends AbstractContainerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    // ── Container liveness ────────────────────────────────────────────────

    @Test
    @DisplayName("PostgreSQL container should be running")
    void postgresContainerShouldBeRunning() {
        assertThat(postgres.isRunning())
                .as("PostgreSQL Testcontainer must be running")
                .isTrue();
    }

    @Test
    @DisplayName("Kafka container should be running")
    void kafkaContainerShouldBeRunning() {
        assertThat(kafka.isRunning())
                .as("Kafka Testcontainer must be running")
                .isTrue();
    }

    // ── Actuator health ───────────────────────────────────────────────────

    @Test
    @DisplayName("Actuator /health endpoint should return HTTP 200")
    void healthEndpointShouldReturn200() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Actuator /health should report application status UP")
    void healthEndpointShouldReportUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getBody()).contains("UP");
    }

    // ── Micrometer metrics (/actuator/metrics) ────────────────────────────

    @Test
    @DisplayName("Actuator /metrics endpoint should return HTTP 200")
    void metricsEndpointShouldReturn200() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/metrics", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("Actuator /metrics should expose standard JVM metrics")
    void metricsEndpointShouldExposeJvmMetrics() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/metrics", String.class);

        String body = response.getBody();
        assertThat(body).as("jvm.memory.used should be listed").contains("jvm.memory.used");
        assertThat(body).as("process.uptime should be listed").contains("process.uptime");
    }

    @Test
    @DisplayName("Actuator /metrics should expose custom trading bot gauges")
    void metricsEndpointShouldExposeCustomTradingMetrics() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/metrics", String.class);

        String body = response.getBody();
        assertThat(body).as("trading.bots.registered gauge should be listed")
                        .contains("trading.bots.registered");
        assertThat(body).as("trading.bots.running gauge should be listed")
                        .contains("trading.bots.running");
    }
}
