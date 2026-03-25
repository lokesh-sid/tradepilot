package tradingbot.agent.api;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;

import tradingbot.AbstractIntegrationTest;
import tradingbot.agent.api.dto.CreateAgentRequest;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;

/**
 * Integration Tests for AgentController
 * 
 * Tests the complete REST API functionality for AI agent management:
 * - Agent CRUD operations (create, get, list, delete)
 * - Agent lifecycle management (activate, pause, stop)
 * - Validation and error handling
 * - Agent goal configuration
 * 
 * Uses real Spring context with mocked external dependencies.
 */
@DisplayName("AgentController Integration Tests")
@org.springframework.security.test.context.support.WithMockUser(username = "user1")
class AgentControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String API_AGENTS = "/api/v1/agents";

    @MockitoBean
    private FuturesExchangeService exchangeService;

    @MockitoBean
    private SentimentAnalyzer sentimentAnalyzer;

    @MockitoBean
    private EventPublisher eventPublisher;
    
        // Use inherited protected performPost, performGet, performDelete from AbstractHttpTest

    // ========== AGENT CREATION TESTS ==========

    @Test
    @DisplayName("Should create agent with valid request and return 201")
    void createAgent_validRequest_shouldReturn201() throws Exception {
        CreateAgentRequest request = new CreateAgentRequest(
                "Bitcoin Trend Follower",
                "MAXIMIZE_PROFIT",
                "Maximize short-term profits through momentum trading",
                "BTCUSDT",
                1000.0,
        null
        );

        performPost(API_AGENTS, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Bitcoin Trend Follower"))
                .andExpect(jsonPath("$.goalType").value("MAXIMIZE_PROFIT"))
                .andExpect(jsonPath("$.tradingSymbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.capital").value(1000.0))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("Should create agent with minimal valid data")
    void createAgent_minimalData_shouldReturn201() throws Exception {
        CreateAgentRequest request = new CreateAgentRequest(
                "ETH Scalper",
                "MAXIMIZE_PROFIT",
                null, // Optional description
                "ETHUSDT",
                500.0,
        null
        );

        performPost(API_AGENTS, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("ETH Scalper"));
    }

    @Test
    @DisplayName("Should reject agent creation with missing name")
    void createAgent_missingName_shouldReturn400() throws Exception {
        String invalidJson = """
                {
                    "goalType": "MAXIMIZE_PROFIT",
                    "tradingSymbol": "BTCUSDT",
                    "capital": 1000.0
                }
                """;

        performPost(API_AGENTS, invalidJson)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("Should reject agent creation with invalid capital")
    void createAgent_invalidCapital_shouldReturn400() throws Exception {
        CreateAgentRequest request = new CreateAgentRequest(
                "Test Agent",
                "MAXIMIZE_PROFIT",
                "Test description",
                "BTCUSDT",
                0.0, // Invalid: must be at least 0.01
                null
        );

        performPost(API_AGENTS, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("Should reject agent creation with invalid symbol")
    void createAgent_invalidSymbol_shouldReturn400() throws Exception {
        CreateAgentRequest request = new CreateAgentRequest(
                "Test Agent",
                "MAXIMIZE_PROFIT",
                "Test description",
                "btc-usdt", // Invalid: lowercase and hyphen
                1000.0,
        null
        );

        performPost(API_AGENTS, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("Should reject agent creation with name too short")
    void createAgent_nameTooShort_shouldReturn400() throws Exception {
        CreateAgentRequest request = new CreateAgentRequest(
                "AB", // Too short: must be at least 3 characters
                "MAXIMIZE_PROFIT",
                "Test description",
                "BTCUSDT",
                1000.0,
        null
        );

        performPost(API_AGENTS, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    // ========== AGENT RETRIEVAL TESTS ==========

    @Test
    @DisplayName("Should get agent by ID and return 200")
    void getAgent_validId_shouldReturn200() throws Exception {
        // Create agent first
        CreateAgentRequest createRequest = new CreateAgentRequest(
                "Test Agent",
                "MAXIMIZE_PROFIT",
                "Test description",
                "BTCUSDT",
                1000.0,
        null
        );
        MvcResult createResult = performPost(API_AGENTS, createRequest)
                .andExpect(status().isCreated())
                .andReturn();
        String agentId = extractAgentId(createResult);

        // Get agent by ID
        performGet(API_AGENTS + "/" + agentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agentId))
                .andExpect(jsonPath("$.name").value("Test Agent"))
                .andExpect(jsonPath("$.goalType").value("MAXIMIZE_PROFIT"))
                .andExpect(jsonPath("$.tradingSymbol").value("BTCUSDT"));
    }

    @Test
    @DisplayName("Should return 404 when getting non-existent agent")
    void getAgent_nonExistent_shouldReturn404() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        performGet(API_AGENTS + "/" + nonExistentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").value(containsString(nonExistentId)));
    }

    @Test
    @DisplayName("Should return 400 with invalid agent ID format")
    void getAgent_invalidIdFormat_shouldReturn400() throws Exception {
        String invalidId = "not-a-valid-uuid";

        performGet(API_AGENTS + "/" + invalidId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Constraint Violation"));
    }

    @Test
    @DisplayName("Should list all agents and return 200")
    void getAllAgents_shouldReturn200WithList() throws Exception {
        // Create multiple agents
        CreateAgentRequest request1 = new CreateAgentRequest(
                "Agent 1", "MAXIMIZE_PROFIT", "First agent", "BTCUSDT", 1000.0, null
        );
        CreateAgentRequest request2 = new CreateAgentRequest(
                "Agent 2", "HEDGE_RISK", "Second agent", "ETHUSDT", 500.0, null
        );

        performPost(API_AGENTS, request1).andExpect(status().isCreated());
        performPost(API_AGENTS, request2).andExpect(status().isCreated());

        // List agents (paginated, scoped to authenticated user)
        performGet(API_AGENTS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    // ========== AGENT LIFECYCLE TESTS ==========

    @Test
    @DisplayName("Should activate agent and return 200")
    void activateAgent_validId_shouldReturn200() throws Exception {
        // Create agent
        CreateAgentRequest createRequest = new CreateAgentRequest(
                "Activation Test Agent",
                "MAXIMIZE_PROFIT",
                "Test activation",
                "BTCUSDT",
                1000.0,
        null
        );
        MvcResult createResult = performPost(API_AGENTS, createRequest)
                .andExpect(status().isCreated())
                .andReturn();
        String agentId = extractAgentId(createResult);

        // Activate agent
        performPost(API_AGENTS + "/" + agentId + "/activate", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agentId))
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("Should pause active agent and return 200")
    void pauseAgent_activeAgent_shouldReturn200() throws Exception {
        // Create and activate agent
        CreateAgentRequest createRequest = new CreateAgentRequest(
                "Pause Test Agent",
                "MAXIMIZE_PROFIT",
                "Test pause",
                "BTCUSDT",
                1000.0,
        null
        );
        MvcResult createResult = performPost(API_AGENTS, createRequest)
                .andExpect(status().isCreated())
                .andReturn();
        String agentId = extractAgentId(createResult);

        performPost(API_AGENTS + "/" + agentId + "/activate", null)
                .andExpect(status().isOk());

        // Pause agent
        performPost(API_AGENTS + "/" + agentId + "/pause", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agentId))
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("Complete agent lifecycle: create → activate → pause → delete")
    void completeAgentLifecycle_shouldSucceed() throws Exception {
        // Create
        CreateAgentRequest createRequest = new CreateAgentRequest(
                "Lifecycle Test Agent",
                "MAXIMIZE_PROFIT",
                "Test complete lifecycle",
                "SOLUSDT",
                750.0,
        null
        );
        MvcResult createResult = performPost(API_AGENTS, createRequest)
                .andExpect(status().isCreated())
                .andReturn();
        String agentId = extractAgentId(createResult);

        // Get agent (verify exists)
        performGet(API_AGENTS + "/" + agentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agentId));

        // Activate
        performPost(API_AGENTS + "/" + agentId + "/activate", null)
                .andExpect(status().isOk());

        // Verify active status
        performGet(API_AGENTS + "/" + agentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agentId));

        // Pause
        performPost(API_AGENTS + "/" + agentId + "/pause", null)
                .andExpect(status().isOk());

        // Verify paused status
        performGet(API_AGENTS + "/" + agentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agentId));

        // Delete (stop)
        performDelete(API_AGENTS + "/" + agentId)
                .andExpect(status().isNoContent());

        // Verify deleted
        performGet(API_AGENTS + "/" + agentId)
                .andExpect(status().isNotFound());
    }

    // ========== AGENT DELETION TESTS ==========

    @Test
    @DisplayName("Should delete agent and return 204")
    void deleteAgent_validId_shouldReturn204() throws Exception {
        // Create agent
        CreateAgentRequest createRequest = new CreateAgentRequest(
                "Delete Test Agent",
                "MAXIMIZE_PROFIT",
                "Test deletion",
                "BTCUSDT",
                1000.0,
        null
        );
        MvcResult createResult = performPost(API_AGENTS, createRequest)
                .andExpect(status().isCreated())
                .andReturn();
        String agentId = extractAgentId(createResult);

        // Delete agent
        performDelete(API_AGENTS + "/" + agentId)
                .andExpect(status().isNoContent());

        // Verify agent no longer exists
        performGet(API_AGENTS + "/" + agentId)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent agent")
    void deleteAgent_nonExistent_shouldReturn404() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        performDelete(API_AGENTS + "/" + nonExistentId)
                .andExpect(status().isNotFound());
    }

    // ========== MULTIPLE AGENTS TESTS ==========

    @Test
    @DisplayName("Should manage multiple agents independently")
    void multipleAgents_shouldBeIndependent() throws Exception {
        // Create first agent
        CreateAgentRequest request1 = new CreateAgentRequest(
                "BTC Agent",
                "MAXIMIZE_PROFIT",
                "Bitcoin trading",
                "BTCUSDT",
                2000.0,
        null
        );
        MvcResult result1 = performPost(API_AGENTS, request1)
                .andExpect(status().isCreated())
                .andReturn();
        String agentId1 = extractAgentId(result1);

        // Create second agent
        CreateAgentRequest request2 = new CreateAgentRequest(
                "ETH Agent",
                "HEDGE_RISK",
                "Ethereum trading",
                "ETHUSDT",
                1500.0,
        null
        );
        MvcResult result2 = performPost(API_AGENTS, request2)
                .andExpect(status().isCreated())
                .andReturn();
        String agentId2 = extractAgentId(result2);

        // Activate first agent
        performPost(API_AGENTS + "/" + agentId1 + "/activate", null)
                .andExpect(status().isOk());

        // Verify both agents exist
        performGet(API_AGENTS + "/" + agentId1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("BTC Agent"));

        performGet(API_AGENTS + "/" + agentId2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ETH Agent"));

        // List should contain both agents
        performGet(API_AGENTS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));

        // Delete first agent
        performDelete(API_AGENTS + "/" + agentId1)
                .andExpect(status().isNoContent());

        // Verify first deleted, second still exists
        performGet(API_AGENTS + "/" + agentId1)
                .andExpect(status().isNotFound());

        performGet(API_AGENTS + "/" + agentId2)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should create agents with different goal types")
    void createAgents_differentGoalTypes_shouldSucceed() throws Exception {
        String[] goalTypes = {"MAXIMIZE_PROFIT", "HEDGE_RISK", "ACCUMULATE_ASSET"};

        for (String goalType : goalTypes) {
            CreateAgentRequest request = new CreateAgentRequest(
                    "Agent " + goalType,
                    goalType,
                    "Testing " + goalType,
                    "BTCUSDT",
                    1000.0,
        null
            );

            performPost(API_AGENTS, request)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.goalType").value(goalType));
        }
    }

    @Test
    @DisplayName("Should create agents with different trading symbols")
    void createAgents_differentSymbols_shouldSucceed() throws Exception {
        String[] symbols = {"BTCUSDT", "ETHUSDT", "SOLUSDT", "ADAUSDT"};

        for (String symbol : symbols) {
            CreateAgentRequest request = new CreateAgentRequest(
                    "Agent " + symbol,
                    "MAXIMIZE_PROFIT",
                    "Trading " + symbol,
                    symbol,
                    1000.0,
        null
            );

            performPost(API_AGENTS, request)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tradingSymbol").value(symbol));
        }
    }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    @DisplayName("Should return 404 when activating non-existent agent")
    void activateAgent_nonExistent_shouldReturn404() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        performPost(API_AGENTS + "/" + nonExistentId + "/activate", null)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 404 when pausing non-existent agent")
    void pauseAgent_nonExistent_shouldReturn404() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();

        performPost(API_AGENTS + "/" + nonExistentId + "/pause", null)
                .andExpect(status().isNotFound());
    }

    // ========== HELPER METHODS ==========

    private String extractAgentId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asText();
    }
}
