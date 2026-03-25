
package tradingbot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;

import tradingbot.agent.TradingAgent;
import tradingbot.agent.manager.AgentManager;
import tradingbot.bot.TradeDirection;
import tradingbot.bot.controller.dto.request.BotStartRequest;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;

/**
 * Proper Integration Test for Futures Trading Bot
 *
 * Tests the complete bot lifecycle through REST API endpoints:
 * - Create bot → Start bot → Check status → Stop bot → Delete bot
 *
 * Uses real Spring context, H2 database, and minimal mocking.
 * Focuses on integration between controller, service, and persistence layers.
 */
@DisplayName("Futures Trading Bot Integration Tests")
class FuturesTradingBotIntegrationTest extends AbstractIntegrationTest {

    private static final String API_V1_BOTS = "/api/v1/bots";

    // Mock all external dependencies for integration testing
    @MockitoBean
    private FuturesExchangeService exchangeService; // Mock to avoid real API calls

    @MockitoBean
    private SentimentAnalyzer sentimentAnalyzer; // Mock to avoid real API calls

    @MockitoBean
    private EventPublisher eventPublisher; // Mock to avoid Kafka dependency

    @Autowired
    private AgentManager agentManager;

    @BeforeEach
    void setUp() {
        new ArrayList<>(agentManager.getAgents()).forEach(a -> agentManager.deleteAgent(a.getId()));
    }

    @Test
    @DisplayName("Complete Bot Lifecycle: Create → Start → Status → Stop → Delete")
    void completeBotLifecycleIntegrationTest() throws Exception {
        // Phase 1: Create a new bot
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.botId").exists())
                .andExpect(jsonPath("$.message").value("Trading bot created successfully"))
                .andReturn();

        // Extract bot ID from response
        JsonNode createResponse = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String botId = createResponse.get("botId").asText();

        // Phase 2: Start the bot in paper trading mode
        BotStartRequest startRequest = new BotStartRequest();
        startRequest.setDirection(TradeDirection.LONG);
        startRequest.setPaper(true);

        performPost(API_V1_BOTS + "/" + botId + "/start", startRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("started in LONG mode (paper)")))
                .andExpect(jsonPath("$.botStatus.running").value(true))
                .andExpect(jsonPath("$.mode").value("paper"))
                .andExpect(jsonPath("$.direction").value("LONG"));

        // Phase 3: Check bot status
        performGet(API_V1_BOTS + "/" + botId + "/status")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.symbol").exists())
                .andExpect(jsonPath("$.leverage").exists())
                .andExpect(jsonPath("$.statusMessage").exists());

        // Phase 4: Stop the bot
        performPut(API_V1_BOTS + "/" + botId + "/stop", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("stopped successfully")))
                .andExpect(jsonPath("$.wasRunning").value(true));

        // Phase 5: Delete the bot
        performDelete(API_V1_BOTS + "/" + botId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Bot " + botId + " deleted successfully"));
    }

    @Test
    @DisplayName("Bot Management: Multiple Bots Creation and Listing")
    void multipleBotsManagementTest() throws Exception {
        // Create multiple bots
        MvcResult result1 = performPost(API_V1_BOTS, null).andExpect(status().isCreated()).andReturn();
        String botId1 = objectMapper.readTree(result1.getResponse().getContentAsString()).get("botId").asText();

        MvcResult result2 = performPost(API_V1_BOTS, null).andExpect(status().isCreated()).andReturn();
        String botId2 = objectMapper.readTree(result2.getResponse().getContentAsString()).get("botId").asText();

        MvcResult result3 = performPost(API_V1_BOTS, null).andExpect(status().isCreated()).andReturn();
        String botId3 = objectMapper.readTree(result3.getResponse().getContentAsString()).get("botId").asText();

        // List all bots
        performGet(API_V1_BOTS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.botIds").isArray())
                .andExpect(jsonPath("$.activeInMemory").value(3))
                .andExpect(jsonPath("$.pagination.totalElements").value(3));

        // Start one bot
        BotStartRequest startRequest = new BotStartRequest();
        startRequest.setDirection(TradeDirection.SHORT);
        startRequest.setPaper(true);

        performPost(API_V1_BOTS + "/" + botId1 + "/start", startRequest)
                .andExpect(status().isOk());

        // Filter by running status
        performGet(API_V1_BOTS + "?status=RUNNING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.botIds").isArray())
                .andExpect(jsonPath("$.pagination.totalElements").value(1));

        // Filter by direction
        performGet(API_V1_BOTS + "?direction=SHORT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.botIds").isArray())
                .andExpect(jsonPath("$.pagination.totalElements").value(1));

        // Clean up
        performDelete(API_V1_BOTS + "/" + botId1).andExpect(status().isOk());
        performDelete(API_V1_BOTS + "/" + botId2).andExpect(status().isOk());
        performDelete(API_V1_BOTS + "/" + botId3).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Error Handling: Bot Not Found Scenarios")
    void botNotFoundErrorHandlingTest() throws Exception {
        String nonExistentBotId = UUID.randomUUID().toString();

        // Try to start non-existent bot
        BotStartRequest startRequest = new BotStartRequest();
        startRequest.setDirection(TradeDirection.LONG);
        startRequest.setPaper(true);

        mockMvc.perform(post("/api/v1/bots/{botId}/start", nonExistentBotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(startRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Bot Not Found"))
                .andExpect(jsonPath("$.detail").value("Trading bot not found with ID: " + nonExistentBotId));

        // Try to get status of non-existent bot
        mockMvc.perform(get("/api/v1/bots/{botId}/status", nonExistentBotId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Bot Not Found"));
    }

    @Test
    @DisplayName("Configuration Management: Leverage and Sentiment Updates")
    void configurationManagementTest() throws Exception {
        // Create bot
        MvcResult createResult = performPost(API_V1_BOTS, null).andExpect(status().isCreated()).andReturn();
        String botId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("botId").asText();

        // Start bot first
        BotStartRequest startRequest = new BotStartRequest();
        startRequest.setDirection(TradeDirection.LONG);
        startRequest.setPaper(true);

        performPost(API_V1_BOTS + "/" + botId + "/start", startRequest)
                .andExpect(status().isOk());

        // Update leverage
        performPost(API_V1_BOTS + "/" + botId + "/leverage", "{\"leverage\": 5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Leverage updated to 5.0x")))
                .andExpect(jsonPath("$.newLeverage").value(5));

        // Enable sentiment analysis
        performPost(API_V1_BOTS + "/" + botId + "/sentiment", "{\"enable\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Sentiment analysis enabled")))
                .andExpect(jsonPath("$.sentimentEnabled").value(true));

        // Clean up
        performDelete(API_V1_BOTS + "/" + botId).andExpect(status().isOk());
    }
}
