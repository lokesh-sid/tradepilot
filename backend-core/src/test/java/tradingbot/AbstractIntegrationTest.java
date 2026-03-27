package tradingbot;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import tradingbot.config.IntegrationTestConfig;

/**
 * Abstract base class for integration tests that test complete workflows
 * through REST API endpoints with real Spring context and in-memory services.
 *
 * Provides generic HTTP request utilities.
 */
@SpringBootTest(
    classes = IntegrationTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(roles = "ADMIN")
@ActiveProfiles("integration")
public abstract class AbstractIntegrationTest extends AbstractContainerSupport {
    // Container management and property injection are handled by AbstractContainerSupport
}