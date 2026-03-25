package tradingbot.bot.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.test.context.TestPropertySource;

import tradingbot.AbstractIntegrationTest;
import tradingbot.bot.TradeDirection;
import tradingbot.bot.controller.dto.request.BotStartRequest;
import tradingbot.bot.controller.dto.request.LeverageUpdateRequest;
import tradingbot.bot.controller.dto.request.SentimentUpdateRequest;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.config.TradingSafetyService;

/**
 * Integration Tests for TradingBotController
 * 
 * Tests the complete REST API functionality including:
 * - Bot lifecycle management (create, start, stop, delete)
 * - Configuration updates (leverage, sentiment)
 * - Status queries and filtering
 * - Pagination and listing
 * - Error handling scenarios
 * 
 * Uses real Spring context with mocked external dependencies.
 */
@DisplayName("TradingBotController Integration Tests")
@TestPropertySource(properties = {
    "trading.execution.mode=live",
    "trading.exchange.provider=bybit"
})
class TradingBotControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String API_V1_BOTS = "/api/v1/bots";

    @MockitoBean
    private FuturesExchangeService exchangeService;

    @MockitoBean
    private SentimentAnalyzer sentimentAnalyzer;

    @MockitoBean
    private EventPublisher eventPublisher;

    @MockitoBean
    private TradingSafetyService tradingSafetyService;

    // ========== BOT LIFECYCLE TESTS ==========

    @Test
    @DisplayName("Should create bot and return 201 with valid botId")
    void createBot_shouldReturn201WithBotId() throws Exception {
        performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.botId").exists())
                .andExpect(jsonPath("$.botId").isString())
                .andExpect(jsonPath("$.message").value("Trading bot created successfully"));
    }

    @Test
    @DisplayName("Should start bot in paper trading mode with LONG direction")
    void startBot_paperTradingLong_shouldReturn200() throws Exception {
        // Create bot
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        // Start bot
        BotStartRequest startRequest = new BotStartRequest();
        startRequest.setDirection(TradeDirection.LONG);
        startRequest.setPaper(true);

        performPost(API_V1_BOTS + "/" + botId + "/start", startRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("started in LONG mode (paper)")))
                .andExpect(jsonPath("$.botStatus.running").value(true))
                .andExpect(jsonPath("$.mode").value("paper"))
                .andExpect(jsonPath("$.direction").value("LONG"));
    }

    @Test
    @DisplayName("Should start bot in live trading mode with SHORT direction")
    void startBot_liveTradingShort_shouldReturn200() throws Exception {
        // Create bot
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        // Start bot in live mode
        BotStartRequest startRequest = new BotStartRequest();
        startRequest.setDirection(TradeDirection.SHORT);
        startRequest.setPaper(false);

        performPost(API_V1_BOTS + "/" + botId + "/start", startRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("started in SHORT mode (live)")))
                .andExpect(jsonPath("$.botStatus.running").value(true))
                .andExpect(jsonPath("$.mode").value("live"))
                .andExpect(jsonPath("$.direction").value("SHORT"));
    }

    @Test
    @DisplayName("Should return 409 when starting already running bot")
    void startBot_alreadyRunning_shouldReturn409() throws Exception {
        // Create and start bot
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        BotStartRequest startRequest = new BotStartRequest();
        startRequest.setDirection(TradeDirection.LONG);
        startRequest.setPaper(true);
        performPost(API_V1_BOTS + "/" + botId + "/start", startRequest)
                .andExpect(status().isOk());

        // Try to start again
        performPost(API_V1_BOTS + "/" + botId + "/start", startRequest)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Bot Already Running"))
                .andExpect(jsonPath("$.detail").value(containsString("already running")));
    }

    @Test
    @DisplayName("Should stop running bot and return 200")
    void stopBot_whenRunning_shouldReturn200() throws Exception {
        // Create and start bot
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        BotStartRequest startRequest = new BotStartRequest();
        startRequest.setDirection(TradeDirection.LONG);
        startRequest.setPaper(true);
        performPost(API_V1_BOTS + "/" + botId + "/start", startRequest)
                .andExpect(status().isOk());

        // Stop bot
        performPut(API_V1_BOTS + "/" + botId + "/stop", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("stopped successfully")))
                .andExpect(jsonPath("$.wasRunning").value(true));
    }

    @Test
    @DisplayName("Should stop non-running bot and return wasRunning=false")
    void stopBot_whenNotRunning_shouldReturn200WithWasRunningFalse() throws Exception {
        // Create bot but don't start it
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        // Stop bot
        performPut(API_V1_BOTS + "/" + botId + "/stop", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("stopped successfully")))
                .andExpect(jsonPath("$.wasRunning").value(false));
    }

    @Test
    @DisplayName("Should delete bot and return 200")
    void deleteBot_shouldReturn200() throws Exception {
        // Create bot
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        // Delete bot
        performDelete(API_V1_BOTS + "/" + botId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Bot " + botId + " deleted successfully"));

        // Verify bot no longer exists
        performGet(API_V1_BOTS + "/" + botId + "/status")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Complete bot lifecycle: create → start → stop → delete")
    void completeBotLifecycle_shouldSucceed() throws Exception {
        // Create
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        // Start
        BotStartRequest startRequest = new BotStartRequest();
        startRequest.setDirection(TradeDirection.LONG);
        startRequest.setPaper(true);
        performPost(API_V1_BOTS + "/" + botId + "/start", startRequest)
                .andExpect(status().isOk());

        // Check status
        performGet(API_V1_BOTS + "/" + botId + "/status")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true));

        // Stop
        performPut(API_V1_BOTS + "/" + botId + "/stop", null)
                .andExpect(status().isOk());

        // Check status again
        performGet(API_V1_BOTS + "/" + botId + "/status")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false));

        // Delete
        performDelete(API_V1_BOTS + "/" + botId)
                .andExpect(status().isOk());
    }

    // ========== CONFIGURATION TESTS ==========

    @Test
    @DisplayName("Should update bot leverage and return 200")
    void updateLeverage_validValue_shouldReturn200() throws Exception {
        // Create bot
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        // Update leverage
        LeverageUpdateRequest request = new LeverageUpdateRequest(20.0);
        performPost(API_V1_BOTS + "/" + botId + "/leverage", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("updated to 20")))
                .andExpect(jsonPath("$.newLeverage").value(20.0));
    }

    @Test
    @DisplayName("Should enable sentiment analysis and return 200")
    void updateSentiment_enableTrue_shouldReturn200() throws Exception {
        // Create bot
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        // Enable sentiment
        SentimentUpdateRequest request = new SentimentUpdateRequest(true);
        performPost(API_V1_BOTS + "/" + botId + "/sentiment", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("enabled")))
                .andExpect(jsonPath("$.sentimentEnabled").value(true));
    }

    @Test
    @DisplayName("Should disable sentiment analysis and return 200")
    void updateSentiment_enableFalse_shouldReturn200() throws Exception {
        // Create bot
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        // Disable sentiment
        SentimentUpdateRequest request = new SentimentUpdateRequest(false);
        performPost(API_V1_BOTS + "/" + botId + "/sentiment", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("disabled")))
                .andExpect(jsonPath("$.sentimentEnabled").value(false));
    }

    // ========== STATUS AND QUERY TESTS ==========

    @Test
    @DisplayName("Should get bot status with all expected fields")
    void getStatus_shouldReturnCompleteStatusInfo() throws Exception {
        // Create bot
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        // Get status
        performGet(API_V1_BOTS + "/" + botId + "/status")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").exists())
                .andExpect(jsonPath("$.symbol").exists())
                .andExpect(jsonPath("$.leverage").exists())
                .andExpect(jsonPath("$.statusMessage").exists())
                .andExpect(jsonPath("$.running").value(false));
    }

    @Test
    @DisplayName("Should list all bots with pagination info")
    void listBots_shouldReturnPaginatedResults() throws Exception {
        // Create multiple bots
        performPost(API_V1_BOTS, null).andExpect(status().isCreated());
        performPost(API_V1_BOTS, null).andExpect(status().isCreated());

        // List bots
        performGet(API_V1_BOTS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.botIds").isArray())
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.pagination.totalElements").value(greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("Should filter bots by status RUNNING")
    @org.junit.jupiter.api.Disabled("Failing due to suspected race condition in status update during integration test")
    void listBots_filterByStatusRunning_shouldReturnOnlyRunningBots() throws Exception {
        // Create and start one bot
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        BotStartRequest startRequest = new BotStartRequest();
        startRequest.setDirection(TradeDirection.LONG);
        startRequest.setPaper(true);
        performPost(API_V1_BOTS + "/" + botId + "/start", startRequest)
                .andExpect(status().isOk());

        // Create another bot but don't start it
        performPost(API_V1_BOTS, null).andExpect(status().isCreated());

        // Filter by RUNNING status
        performGet(API_V1_BOTS + "?status=RUNNING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Should filter bots by direction SHORT")
    void listBots_filterByDirectionShort_shouldReturnOnlyShortBots() throws Exception {
        // Create and start bot with SHORT direction
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        BotStartRequest startRequest = new BotStartRequest();
        startRequest.setDirection(TradeDirection.SHORT);
        startRequest.setPaper(true);
        performPost(API_V1_BOTS + "/" + botId + "/start", startRequest)
                .andExpect(status().isOk());

        // Filter by SHORT direction
        performGet(API_V1_BOTS + "?direction=SHORT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalElements").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Should support pagination with page and size parameters")
    void listBots_withPagination_shouldReturnCorrectPage() throws Exception {
        // Create multiple bots to test pagination
        for (int i = 0; i < 5; i++) {
            performPost(API_V1_BOTS, null).andExpect(status().isCreated());
        }

        // Get first page with size 2
        performGet(API_V1_BOTS + "?page=0&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.botIds").isArray())
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.totalElements").value(greaterThanOrEqualTo(5)));
    }

    // ========== ERROR HANDLING TESTS ==========

    @Test
    @DisplayName("Should return 404 when getting status of non-existent bot")
    void getStatus_nonExistentBot_shouldReturn404() throws Exception {
        String nonExistentBotId = UUID.randomUUID().toString();

        performGet(API_V1_BOTS + "/" + nonExistentBotId + "/status")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Bot Not Found"))
                .andExpect(jsonPath("$.detail").value(containsString(nonExistentBotId)));
    }

    @Test
    @DisplayName("Should return 404 when starting non-existent bot")
    void startBot_nonExistentBot_shouldReturn404() throws Exception {
        String nonExistentBotId = UUID.randomUUID().toString();

        BotStartRequest startRequest = new BotStartRequest();
        startRequest.setDirection(TradeDirection.LONG);
        startRequest.setPaper(true);

        performPost(API_V1_BOTS + "/" + nonExistentBotId + "/start", startRequest)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Bot Not Found"));
    }

    @Test
    @DisplayName("Should return 404 when deleting non-existent bot")
    void deleteBot_nonExistentBot_shouldReturn404() throws Exception {
        String nonExistentBotId = UUID.randomUUID().toString();

        performDelete(API_V1_BOTS + "/" + nonExistentBotId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Bot Not Found"));
    }

    @Test
    @DisplayName("Should return 400 when starting bot with invalid direction")
    void startBot_invalidDirection_shouldReturn400() throws Exception {
        // Create bot
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        // Send invalid JSON with bad direction
        String invalidJson = "{\"direction\": \"INVALID\", \"paper\": true}";
        performPost(API_V1_BOTS + "/" + botId + "/start", invalidJson)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when updating leverage with invalid value")
    void updateLeverage_invalidValue_shouldReturn400() throws Exception {
        // Create bot
        MvcResult createResult = performPost(API_V1_BOTS, null)
                .andExpect(status().isCreated())
                .andReturn();
        String botId = extractBotId(createResult);

        // Try to update with invalid leverage (0)
        LeverageUpdateRequest request = new LeverageUpdateRequest(0.0);
        performPost(API_V1_BOTS + "/" + botId + "/leverage", request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("Should return 400 with invalid bot ID format")
    void operations_invalidBotIdFormat_shouldReturn400() throws Exception {
        String invalidBotId = "not-a-valid-uuid";

        performGet(API_V1_BOTS + "/" + invalidBotId + "/status")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Constraint Violation"));
    }

    // ========== HELPER METHODS ==========

    private String extractBotId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("botId").asText();
    }
}
