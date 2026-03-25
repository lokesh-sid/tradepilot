package tradingbot.bot.grpc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import tradingbot.bot.service.BotCacheService;
import tradingbot.grpc.bot.BotManagementServiceGrpc;
import tradingbot.grpc.bot.BotStatusRequest;
import tradingbot.grpc.bot.BotStatusResponse;
import tradingbot.grpc.bot.CreateBotRequest;
import tradingbot.grpc.bot.CreateBotResponse;
import tradingbot.grpc.bot.DeleteBotRequest;
import tradingbot.grpc.bot.DeleteBotResponse;
import tradingbot.grpc.bot.ListBotsRequest;
import tradingbot.grpc.bot.ListBotsResponse;
import tradingbot.grpc.bot.PauseBotRequest;
import tradingbot.grpc.bot.PauseBotResponse;
import tradingbot.grpc.bot.ResumeBotRequest;
import tradingbot.grpc.bot.ResumeBotResponse;
import tradingbot.grpc.bot.StartBotRequest;
import tradingbot.grpc.bot.StartBotResponse;
import tradingbot.grpc.bot.StopBotRequest;
import tradingbot.grpc.bot.StopBotResponse;
import tradingbot.grpc.bot.UpdateBotRequest;
import tradingbot.grpc.bot.UpdateBotResponse;
import tradingbot.grpc.common.TradingConfig;

/**
 * Integration tests for BotManagementServiceImpl using real gRPC client-server communication
 * 
 * These tests start the gRPC server and use a real gRPC client to test end-to-end functionality
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(
    properties = {
        "grpc.server.port=9091",
        "grpc.server.security.enabled=false",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.listener.auto-startup=false",
        "spring.main.allow-bean-definition-overriding=true"
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("BotManagementService gRPC Integration Tests")
class BotManagementServiceIntegrationTest {

    @SuppressWarnings("resource")
    static final GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        redis.start();
    }

    @DynamicPropertySource
    static void configureRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    
    @Autowired
    private BotCacheService botCacheService;
    
    private ManagedChannel channel;
    private BotManagementServiceGrpc.BotManagementServiceBlockingStub blockingStub;
    
    private static final String TEST_USER_ID = "integration-test-user";
    private static final String TEST_SYMBOL = "BTCUSDT";
    
    @BeforeEach
    void setUp() {
        // Create gRPC channel and stub
        channel = ManagedChannelBuilder.forAddress("localhost", 9091)
                .usePlaintext()
                .build();
        
        blockingStub = BotManagementServiceGrpc.newBlockingStub(channel);
        
        // Clear any existing bot states
        if (botCacheService != null) {
            botCacheService.getAllBotIds().forEach(botCacheService::deleteBotState);
        }
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (channel != null) {
            channel.shutdown();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
    
    // ==================== CREATE AND START BOT FLOW ====================
    
    @Test
    @DisplayName("Integration: Should create, start, and get status of a bot")
    void testCreateStartAndGetStatusFlow() {
        // Step 1: Create a bot
        TradingConfig config = TradingConfig.newBuilder()
                .setSymbol(TEST_SYMBOL)
                .setInitialCapital(1000.0)
                .setLeverage(3)
                .setTrailingStopPercentage(2.0)
                .setPaperTrading(true)
                .build();
        
        CreateBotRequest createRequest = CreateBotRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setConfig(config)
                .build();
        
        CreateBotResponse createResponse = blockingStub.createBot(createRequest);
        
        assertTrue(createResponse.getSuccess());
        assertNotNull(createResponse.getBotId());
        assertFalse(createResponse.getBotId().isEmpty());
        
        String botId = createResponse.getBotId();
        
        // Step 2: Start the bot
        StartBotRequest startRequest = StartBotRequest.newBuilder()
                .setBotId(botId)
                .build();
        
        StartBotResponse startResponse = blockingStub.startBot(startRequest);
        
        assertTrue(startResponse.getSuccess());
        assertEquals(tradingbot.grpc.common.BotState.RUNNING, startResponse.getState());
        
        // Step 3: Get bot status
        BotStatusRequest statusRequest = BotStatusRequest.newBuilder()
                .setBotId(botId)
                .build();
        
        BotStatusResponse statusResponse = blockingStub.getBotStatus(statusRequest);
        
        assertEquals(botId, statusResponse.getBotId());
        assertEquals(tradingbot.grpc.common.BotState.RUNNING, statusResponse.getState());
        assertFalse(statusResponse.hasError());
    }
    
    // ==================== PAUSE AND RESUME FLOW ====================
    
    @Test
    @DisplayName("Integration: Should pause and resume a running bot")
    void testPauseAndResumeFlow() {
        // Create and start a bot
        String botId = createAndStartBot();
        
        // Pause the bot
        PauseBotRequest pauseRequest = PauseBotRequest.newBuilder()
                .setBotId(botId)
                .build();
        
        PauseBotResponse pauseResponse = blockingStub.pauseBot(pauseRequest);
        
        assertTrue(pauseResponse.getSuccess());
        assertEquals(tradingbot.grpc.common.BotState.PAUSED, pauseResponse.getState());
        
        // Verify paused status
        BotStatusRequest statusRequest = BotStatusRequest.newBuilder()
                .setBotId(botId)
                .build();
        
        BotStatusResponse pausedStatus = blockingStub.getBotStatus(statusRequest);
        assertEquals(tradingbot.grpc.common.BotState.PAUSED, pausedStatus.getState());
        
        // Resume the bot
        ResumeBotRequest resumeRequest = ResumeBotRequest.newBuilder()
                .setBotId(botId)
                .build();
        
        ResumeBotResponse resumeResponse = blockingStub.resumeBot(resumeRequest);
        
        assertTrue(resumeResponse.getSuccess());
        assertEquals(tradingbot.grpc.common.BotState.RUNNING, resumeResponse.getState());
        
        // Verify running status
        BotStatusResponse runningStatus = blockingStub.getBotStatus(statusRequest);
        assertEquals(tradingbot.grpc.common.BotState.RUNNING, runningStatus.getState());
    }
    
    // ==================== UPDATE BOT FLOW ====================
    
    @Test
    @DisplayName("Integration: Should update bot configuration")
    void testUpdateBotFlow() {
        // Create a bot
        String botId = createAndStartBot();
        
        // Update the bot with new leverage
        TradingConfig updatedConfig = TradingConfig.newBuilder()
                .setLeverage(5)
                .build();
        
        UpdateBotRequest updateRequest = UpdateBotRequest.newBuilder()
                .setBotId(botId)
                .setConfig(updatedConfig)
                .build();
        
        UpdateBotResponse updateResponse = blockingStub.updateBot(updateRequest);
        
        assertTrue(updateResponse.getSuccess());
        assertEquals("Bot updated successfully", updateResponse.getMessage());
    }
    
    // ==================== LIST BOTS FLOW ====================
    
    @Test
    @DisplayName("Integration: Should list multiple bots")
    void testListBotsFlow() {
        // Create multiple bots
        String botId1 = createAndStartBot();
        String botId2 = createAndStartBot();
        String botId3 = createAndStartBot();
        
        // List all bots
        ListBotsRequest listRequest = ListBotsRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setPage(1)
                .setPageSize(10)
                .build();
        
        ListBotsResponse listResponse = blockingStub.listBots(listRequest);
        
        assertEquals(3, listResponse.getTotalCount());
        assertEquals(3, listResponse.getBotsCount());
        assertFalse(listResponse.hasError());
        
        // Verify all bot IDs are present
        assertTrue(listResponse.getBotsList().stream()
                .anyMatch(bot -> bot.getBotId().equals(botId1)));
        assertTrue(listResponse.getBotsList().stream()
                .anyMatch(bot -> bot.getBotId().equals(botId2)));
        assertTrue(listResponse.getBotsList().stream()
                .anyMatch(bot -> bot.getBotId().equals(botId3)));
    }
    
    // ==================== STOP AND DELETE FLOW ====================
    
    @Test
    @DisplayName("Integration: Should stop and delete a bot")
    void testStopAndDeleteFlow() {
        // Create and start a bot
        String botId = createAndStartBot();
        
        // Stop the bot
        StopBotRequest stopRequest = StopBotRequest.newBuilder()
                .setBotId(botId)
                .build();
        
        StopBotResponse stopResponse = blockingStub.stopBot(stopRequest);
        
        assertTrue(stopResponse.getSuccess());
        assertEquals(tradingbot.grpc.common.BotState.STOPPED, stopResponse.getState());
        
        // Verify stopped status
        BotStatusRequest statusRequest = BotStatusRequest.newBuilder()
                .setBotId(botId)
                .build();
        
        BotStatusResponse stoppedStatus = blockingStub.getBotStatus(statusRequest);
        assertEquals(tradingbot.grpc.common.BotState.STOPPED, stoppedStatus.getState());
        
        // Delete the bot
        DeleteBotRequest deleteRequest = DeleteBotRequest.newBuilder()
                .setBotId(botId)
                .build();
        
        DeleteBotResponse deleteResponse = blockingStub.deleteBot(deleteRequest);
        
        assertTrue(deleteResponse.getSuccess());
        assertEquals("Bot deleted successfully", deleteResponse.getMessage());
        
        // Verify bot no longer exists
        try {
            blockingStub.getBotStatus(statusRequest);
            fail("Expected StatusRuntimeException for deleted bot");
        } catch (StatusRuntimeException e) {
            // Expected - bot should not be found
            assertTrue(e.getMessage().contains("NOT_FOUND") || 
                      e.getMessage().contains("Bot not found"));
        }
    }
    
    // ==================== ERROR HANDLING ====================
    
    @Test
    @DisplayName("Integration: Should handle non-existent bot gracefully")
    void testNonExistentBotError() {
        // Try to start a non-existent bot
        StartBotRequest startRequest = StartBotRequest.newBuilder()
                .setBotId("non-existent-bot-id")
                .build();
        
        StartBotResponse startResponse = blockingStub.startBot(startRequest);
        
        assertFalse(startResponse.getSuccess());
        assertTrue(startResponse.hasError());
        assertEquals(404, startResponse.getError().getCode());
        assertEquals("Bot not found", startResponse.getMessage());
    }
    
    @Test
    @DisplayName("Integration: Should handle stopping non-existent bot")
    void testStopNonExistentBot() {
        // Try to stop a non-existent bot
        StopBotRequest stopRequest = StopBotRequest.newBuilder()
                .setBotId("non-existent-bot-id")
                .build();
        
        StopBotResponse stopResponse = blockingStub.stopBot(stopRequest);
        
        assertFalse(stopResponse.getSuccess());
        assertTrue(stopResponse.hasError());
        assertEquals(404, stopResponse.getError().getCode());
    }
    
    @Test
    @DisplayName("Integration: Should handle deleting non-existent bot")
    void testDeleteNonExistentBot() {
        // Try to delete a non-existent bot
        DeleteBotRequest deleteRequest = DeleteBotRequest.newBuilder()
                .setBotId("non-existent-bot-id")
                .build();
        
        DeleteBotResponse deleteResponse = blockingStub.deleteBot(deleteRequest);
        
        assertFalse(deleteResponse.getSuccess());
        assertTrue(deleteResponse.hasError());
        assertEquals(404, deleteResponse.getError().getCode());
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Helper method to create and start a bot, returning the bot ID
     */
    private String createAndStartBot() {
        TradingConfig config = TradingConfig.newBuilder()
                .setSymbol(TEST_SYMBOL)
                .setInitialCapital(1000.0)
                .setLeverage(3)
                .setTrailingStopPercentage(2.0)
                .setPaperTrading(true)
                .build();
        
        CreateBotRequest createRequest = CreateBotRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setConfig(config)
                .build();
        
        CreateBotResponse createResponse = blockingStub.createBot(createRequest);
        assertTrue(createResponse.getSuccess());
        
        String botId = createResponse.getBotId();
        
        StartBotRequest startRequest = StartBotRequest.newBuilder()
                .setBotId(botId)
                .build();
        
        StartBotResponse startResponse = blockingStub.startBot(startRequest);
        assertTrue(startResponse.getSuccess());
        
        return botId;
    }
}
