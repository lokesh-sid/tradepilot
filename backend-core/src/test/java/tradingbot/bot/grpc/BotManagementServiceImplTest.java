package tradingbot.bot.grpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import tradingbot.bot.controller.dto.BotState;
import tradingbot.bot.service.BotCacheService;
import tradingbot.config.TradingConfig;
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

/**
 * Unit tests for BotManagementServiceImpl gRPC service
 * 
 * Tests all gRPC endpoints using Mockito for dependencies and StreamObserver mocking
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BotManagementServiceImpl gRPC Tests")
class BotManagementServiceImplTest {
    
    @Mock
    private BotCacheService botCacheService;
    
    @Mock
    private StreamObserver<CreateBotResponse> createBotResponseObserver;
    
    @Mock
    private StreamObserver<StartBotResponse> startBotResponseObserver;
    
    @Mock
    private StreamObserver<StopBotResponse> stopBotResponseObserver;
    
    @Mock
    private StreamObserver<BotStatusResponse> botStatusResponseObserver;
    
    @Mock
    private StreamObserver<UpdateBotResponse> updateBotResponseObserver;
    
    @Mock
    private StreamObserver<DeleteBotResponse> deleteBotResponseObserver;
    
    @Mock
    private StreamObserver<ListBotsResponse> listBotsResponseObserver;
    
    @Mock
    private StreamObserver<PauseBotResponse> pauseBotResponseObserver;
    
    @Mock
    private StreamObserver<ResumeBotResponse> resumeBotResponseObserver;
    
    @InjectMocks
    private BotManagementServiceImpl botManagementService;
    
    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_BOT_ID = "bot-123";
    private static final String TEST_SYMBOL = "BTCUSDT";
    
    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(botCacheService, createBotResponseObserver, startBotResponseObserver, 
              stopBotResponseObserver, botStatusResponseObserver, updateBotResponseObserver,
              deleteBotResponseObserver, listBotsResponseObserver, pauseBotResponseObserver,
              resumeBotResponseObserver);
    }
    
    // ==================== CREATE BOT TESTS ====================
    
    @Test
    @DisplayName("Should create bot successfully with valid configuration")
    void testCreateBotSuccess() {
        // Given
        tradingbot.grpc.common.TradingConfig protoConfig = tradingbot.grpc.common.TradingConfig.newBuilder()
                .setSymbol(TEST_SYMBOL)
                .setInitialCapital(1000.0)
                .setLeverage(3)
                .setTrailingStopPercentage(2.0)
                .setPaperTrading(true)
                .build();
        
        CreateBotRequest request = CreateBotRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setConfig(protoConfig)
                .build();
        
        // When
        botManagementService.createBot(request, createBotResponseObserver);
        
        // Then
        ArgumentCaptor<CreateBotResponse> responseCaptor = ArgumentCaptor.forClass(CreateBotResponse.class);
        verify(createBotResponseObserver).onNext(responseCaptor.capture());
        verify(createBotResponseObserver).onCompleted();
        
        CreateBotResponse response = responseCaptor.getValue();
        assertTrue(response.getSuccess());
        assertNotNull(response.getBotId());
        assertFalse(response.getBotId().isEmpty());
        assertEquals("Bot created successfully", response.getMessage());
        
        // Verify bot state was saved
        verify(botCacheService).saveBotState(anyString(), any(BotState.class));
    }
    
    @Test
    @DisplayName("Should handle bot creation failure gracefully")
    void testCreateBotFailure() {
        // Given
        tradingbot.grpc.common.TradingConfig protoConfig = tradingbot.grpc.common.TradingConfig.newBuilder()
                .setSymbol(TEST_SYMBOL)
                .setInitialCapital(1000.0)
                .setLeverage(3)
                .setTrailingStopPercentage(2.0)
                .setPaperTrading(true)
                .build();
        
        CreateBotRequest request = CreateBotRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setConfig(protoConfig)
                .build();
        
        doThrow(new RuntimeException("Database error"))
                .when(botCacheService).saveBotState(anyString(), any(BotState.class));
        
        // When
        botManagementService.createBot(request, createBotResponseObserver);
        
        // Then
        ArgumentCaptor<CreateBotResponse> responseCaptor = ArgumentCaptor.forClass(CreateBotResponse.class);
        verify(createBotResponseObserver).onNext(responseCaptor.capture());
        verify(createBotResponseObserver).onCompleted();
        
        CreateBotResponse response = responseCaptor.getValue();
        assertFalse(response.getSuccess());
        assertEquals("Failed to create bot", response.getMessage());
        assertTrue(response.hasError());
        assertEquals(500, response.getError().getCode());
    }
    
    // ==================== START BOT TESTS ====================
    
    @Test
    @DisplayName("Should start bot successfully when bot exists")
    void testStartBotSuccess() {
        // Given
        BotState existingBot = createTestBotState(TEST_BOT_ID, false);
        when(botCacheService.getBotState(TEST_BOT_ID)).thenReturn(existingBot);
        
        StartBotRequest request = StartBotRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .build();
        
        // When
        botManagementService.startBot(request, startBotResponseObserver);
        
        // Then
        ArgumentCaptor<StartBotResponse> responseCaptor = ArgumentCaptor.forClass(StartBotResponse.class);
        verify(startBotResponseObserver).onNext(responseCaptor.capture());
        verify(startBotResponseObserver).onCompleted();
        
        StartBotResponse response = responseCaptor.getValue();
        assertTrue(response.getSuccess());
        assertEquals("Bot started successfully", response.getMessage());
        assertEquals(tradingbot.grpc.common.BotState.RUNNING, response.getState());
        
        // Verify bot state was updated
        verify(botCacheService).saveBotState(eq(TEST_BOT_ID), any(BotState.class));
    }
    
    @Test
    @DisplayName("Should return error when starting non-existent bot")
    void testStartBotNotFound() {
        // Given
        when(botCacheService.getBotState(TEST_BOT_ID)).thenReturn(null);
        
        StartBotRequest request = StartBotRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .build();
        
        // When
        botManagementService.startBot(request, startBotResponseObserver);
        
        // Then
        ArgumentCaptor<StartBotResponse> responseCaptor = ArgumentCaptor.forClass(StartBotResponse.class);
        verify(startBotResponseObserver).onNext(responseCaptor.capture());
        verify(startBotResponseObserver).onCompleted();
        
        StartBotResponse response = responseCaptor.getValue();
        assertFalse(response.getSuccess());
        assertEquals("Bot not found", response.getMessage());
        assertTrue(response.hasError());
        assertEquals(404, response.getError().getCode());
    }
    
    @Test
    @DisplayName("Should handle start bot exception gracefully")
    void testStartBotException() {
        // Given
        when(botCacheService.getBotState(TEST_BOT_ID))
                .thenThrow(new RuntimeException("Redis connection failed"));
        
        StartBotRequest request = StartBotRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .build();
        
        // When
        botManagementService.startBot(request, startBotResponseObserver);
        
        // Then
        ArgumentCaptor<StartBotResponse> responseCaptor = ArgumentCaptor.forClass(StartBotResponse.class);
        verify(startBotResponseObserver).onNext(responseCaptor.capture());
        verify(startBotResponseObserver).onCompleted();
        
        StartBotResponse response = responseCaptor.getValue();
        assertFalse(response.getSuccess());
        assertEquals("Failed to start bot", response.getMessage());
        assertEquals(tradingbot.grpc.common.BotState.ERROR, response.getState());
    }
    
    // ==================== STOP BOT TESTS ====================
    
    @Test
    @DisplayName("Should stop bot successfully when bot is running")
    void testStopBotSuccess() {
        // Given
        BotState runningBot = createTestBotState(TEST_BOT_ID, true);
        when(botCacheService.getBotState(TEST_BOT_ID)).thenReturn(runningBot);
        
        StopBotRequest request = StopBotRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .build();
        
        // When
        botManagementService.stopBot(request, stopBotResponseObserver);
        
        // Then
        ArgumentCaptor<StopBotResponse> responseCaptor = ArgumentCaptor.forClass(StopBotResponse.class);
        verify(stopBotResponseObserver).onNext(responseCaptor.capture());
        verify(stopBotResponseObserver).onCompleted();
        
        StopBotResponse response = responseCaptor.getValue();
        assertTrue(response.getSuccess());
        assertEquals("Bot stopped successfully", response.getMessage());
        assertEquals(tradingbot.grpc.common.BotState.STOPPED, response.getState());
        
        // Verify bot state was updated
        verify(botCacheService).saveBotState(eq(TEST_BOT_ID), any(BotState.class));
    }
    
    @Test
    @DisplayName("Should return error when stopping non-existent bot")
    void testStopBotNotFound() {
        // Given
        when(botCacheService.getBotState(TEST_BOT_ID)).thenReturn(null);
        
        StopBotRequest request = StopBotRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .build();
        
        // When
        botManagementService.stopBot(request, stopBotResponseObserver);
        
        // Then
        ArgumentCaptor<StopBotResponse> responseCaptor = ArgumentCaptor.forClass(StopBotResponse.class);
        verify(stopBotResponseObserver).onNext(responseCaptor.capture());
        verify(stopBotResponseObserver).onCompleted();
        
        StopBotResponse response = responseCaptor.getValue();
        assertFalse(response.getSuccess());
        assertEquals("Bot not found", response.getMessage());
        assertTrue(response.hasError());
        assertEquals(404, response.getError().getCode());
    }
    
    // ==================== GET BOT STATUS TESTS ====================
    
    @Test
    @DisplayName("Should retrieve bot status successfully")
    void testGetBotStatusSuccess() {
        // Given
        BotState existingBot = createTestBotState(TEST_BOT_ID, true);
        when(botCacheService.getBotState(TEST_BOT_ID)).thenReturn(existingBot);
        
        BotStatusRequest request = BotStatusRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .build();
        
        // When
        botManagementService.getBotStatus(request, botStatusResponseObserver);
        
        // Then
        ArgumentCaptor<BotStatusResponse> responseCaptor = ArgumentCaptor.forClass(BotStatusResponse.class);
        verify(botStatusResponseObserver).onNext(responseCaptor.capture());
        verify(botStatusResponseObserver).onCompleted();
        
        BotStatusResponse response = responseCaptor.getValue();
        assertEquals(TEST_BOT_ID, response.getBotId());
        assertEquals(tradingbot.grpc.common.BotState.RUNNING, response.getState());
        assertFalse(response.hasError());
    }
    
    @Test
    @DisplayName("Should return error for non-existent bot status")
    void testGetBotStatusNotFound() {
        // Given
        when(botCacheService.getBotState(TEST_BOT_ID)).thenReturn(null);

        BotStatusRequest request = BotStatusRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .build();

        // When
        botManagementService.getBotStatus(request, botStatusResponseObserver);

        // Then - onError should be called with NOT_FOUND status
        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(botStatusResponseObserver).onError(errorCaptor.capture());
        verify(botStatusResponseObserver, never()).onNext(any());

        Throwable error = errorCaptor.getValue();
        assertInstanceOf(StatusRuntimeException.class, error);
        assertEquals(Status.NOT_FOUND.getCode(), ((StatusRuntimeException) error).getStatus().getCode());
    }
    
    // ==================== UPDATE BOT TESTS ====================
    
    @Test
    @DisplayName("Should update bot configuration successfully")
    void testUpdateBotSuccess() {
        // Given
        BotState existingBot = createTestBotState(TEST_BOT_ID, true);
        when(botCacheService.getBotState(TEST_BOT_ID)).thenReturn(existingBot);
        
        tradingbot.grpc.common.TradingConfig updatedConfig = tradingbot.grpc.common.TradingConfig.newBuilder()
                .setLeverage(5)
                .build();
        
        UpdateBotRequest request = UpdateBotRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .setConfig(updatedConfig)
                .build();
        
        // When
        botManagementService.updateBot(request, updateBotResponseObserver);
        
        // Then
        ArgumentCaptor<UpdateBotResponse> responseCaptor = ArgumentCaptor.forClass(UpdateBotResponse.class);
        verify(updateBotResponseObserver).onNext(responseCaptor.capture());
        verify(updateBotResponseObserver).onCompleted();
        
        UpdateBotResponse response = responseCaptor.getValue();
        assertTrue(response.getSuccess());
        assertEquals("Bot updated successfully", response.getMessage());
        
        // Verify bot state was saved
        verify(botCacheService).saveBotState(eq(TEST_BOT_ID), any(BotState.class));
    }
    
    @Test
    @DisplayName("Should return error when updating non-existent bot")
    void testUpdateBotNotFound() {
        // Given
        when(botCacheService.getBotState(TEST_BOT_ID)).thenReturn(null);
        
        UpdateBotRequest request = UpdateBotRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .build();
        
        // When
        botManagementService.updateBot(request, updateBotResponseObserver);
        
        // Then
        ArgumentCaptor<UpdateBotResponse> responseCaptor = ArgumentCaptor.forClass(UpdateBotResponse.class);
        verify(updateBotResponseObserver).onNext(responseCaptor.capture());
        verify(updateBotResponseObserver).onCompleted();
        
        UpdateBotResponse response = responseCaptor.getValue();
        assertFalse(response.getSuccess());
        assertEquals("Bot not found", response.getMessage());
        assertTrue(response.hasError());
        assertEquals(404, response.getError().getCode());
    }
    
    // ==================== DELETE BOT TESTS ====================
    
    @Test
    @DisplayName("Should delete bot successfully")
    void testDeleteBotSuccess() {
        // Given
        BotState existingBot = createTestBotState(TEST_BOT_ID, false);
        when(botCacheService.getBotState(TEST_BOT_ID)).thenReturn(existingBot);
        
        DeleteBotRequest request = DeleteBotRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .build();
        
        // When
        botManagementService.deleteBot(request, deleteBotResponseObserver);
        
        // Then
        ArgumentCaptor<DeleteBotResponse> responseCaptor = ArgumentCaptor.forClass(DeleteBotResponse.class);
        verify(deleteBotResponseObserver).onNext(responseCaptor.capture());
        verify(deleteBotResponseObserver).onCompleted();
        
        DeleteBotResponse response = responseCaptor.getValue();
        assertTrue(response.getSuccess());
        assertEquals("Bot deleted successfully", response.getMessage());
        
        // Verify bot was deleted from cache
        verify(botCacheService).deleteBotState(TEST_BOT_ID);
    }
    
    @Test
    @DisplayName("Should return error when deleting non-existent bot")
    void testDeleteBotNotFound() {
        // Given
        when(botCacheService.getBotState(TEST_BOT_ID)).thenReturn(null);
        
        DeleteBotRequest request = DeleteBotRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .build();
        
        // When
        botManagementService.deleteBot(request, deleteBotResponseObserver);
        
        // Then
        ArgumentCaptor<DeleteBotResponse> responseCaptor = ArgumentCaptor.forClass(DeleteBotResponse.class);
        verify(deleteBotResponseObserver).onNext(responseCaptor.capture());
        verify(deleteBotResponseObserver).onCompleted();
        
        DeleteBotResponse response = responseCaptor.getValue();
        assertFalse(response.getSuccess());
        assertEquals("Bot not found", response.getMessage());
        assertTrue(response.hasError());
        assertEquals(404, response.getError().getCode());
    }
    
    // ==================== LIST BOTS TESTS ====================
    
    @Test
    @DisplayName("Should list all bots successfully")
    void testListBotsSuccess() {
        // Given
        Set<String> botIds = new HashSet<>();
        botIds.add("bot-1");
        botIds.add("bot-2");
        botIds.add("bot-3");
        
        when(botCacheService.getAllBotIds()).thenReturn(botIds);
        when(botCacheService.getBotState("bot-1")).thenReturn(createTestBotState("bot-1", true));
        when(botCacheService.getBotState("bot-2")).thenReturn(createTestBotState("bot-2", false));
        when(botCacheService.getBotState("bot-3")).thenReturn(createTestBotState("bot-3", true));
        
        ListBotsRequest request = ListBotsRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setPage(1)
                .setPageSize(10)
                .build();
        
        // When
        botManagementService.listBots(request, listBotsResponseObserver);
        
        // Then
        ArgumentCaptor<ListBotsResponse> responseCaptor = ArgumentCaptor.forClass(ListBotsResponse.class);
        verify(listBotsResponseObserver).onNext(responseCaptor.capture());
        verify(listBotsResponseObserver).onCompleted();
        
        ListBotsResponse response = responseCaptor.getValue();
        assertEquals(3, response.getTotalCount());
        assertEquals(3, response.getBotsCount());
        assertEquals(1, response.getPage());
        assertEquals(10, response.getPageSize());
        assertFalse(response.hasError());
    }
    
    @Test
    @DisplayName("Should return empty list when no bots exist")
    void testListBotsEmpty() {
        // Given
        Set<String> emptyBotIds = new HashSet<>();
        when(botCacheService.getAllBotIds()).thenReturn(emptyBotIds);
        
        ListBotsRequest request = ListBotsRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setPage(1)
                .setPageSize(10)
                .build();
        
        // When
        botManagementService.listBots(request, listBotsResponseObserver);
        
        // Then
        ArgumentCaptor<ListBotsResponse> responseCaptor = ArgumentCaptor.forClass(ListBotsResponse.class);
        verify(listBotsResponseObserver).onNext(responseCaptor.capture());
        verify(listBotsResponseObserver).onCompleted();
        
        ListBotsResponse response = responseCaptor.getValue();
        assertEquals(0, response.getTotalCount());
        assertEquals(0, response.getBotsCount());
        assertFalse(response.hasError());
    }
    
    // ==================== PAUSE BOT TESTS ====================
    
    @Test
    @DisplayName("Should pause bot successfully")
    void testPauseBotSuccess() {
        // Given
        BotState runningBot = createTestBotState(TEST_BOT_ID, true);
        when(botCacheService.getBotState(TEST_BOT_ID)).thenReturn(runningBot);
        
        PauseBotRequest request = PauseBotRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .build();
        
        // When
        botManagementService.pauseBot(request, pauseBotResponseObserver);
        
        // Then
        ArgumentCaptor<PauseBotResponse> responseCaptor = ArgumentCaptor.forClass(PauseBotResponse.class);
        verify(pauseBotResponseObserver).onNext(responseCaptor.capture());
        verify(pauseBotResponseObserver).onCompleted();
        
        PauseBotResponse response = responseCaptor.getValue();
        assertTrue(response.getSuccess());
        assertEquals("Bot paused successfully", response.getMessage());
        assertEquals(tradingbot.grpc.common.BotState.PAUSED, response.getState());
        
        // Verify bot state was updated
        verify(botCacheService).saveBotState(eq(TEST_BOT_ID), any(BotState.class));
    }
    
    @Test
    @DisplayName("Should return error when pausing non-existent bot")
    void testPauseBotNotFound() {
        // Given
        when(botCacheService.getBotState(TEST_BOT_ID)).thenReturn(null);
        
        PauseBotRequest request = PauseBotRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .build();
        
        // When
        botManagementService.pauseBot(request, pauseBotResponseObserver);
        
        // Then
        ArgumentCaptor<PauseBotResponse> responseCaptor = ArgumentCaptor.forClass(PauseBotResponse.class);
        verify(pauseBotResponseObserver).onNext(responseCaptor.capture());
        verify(pauseBotResponseObserver).onCompleted();
        
        PauseBotResponse response = responseCaptor.getValue();
        assertFalse(response.getSuccess());
        assertEquals("Bot not found", response.getMessage());
        assertTrue(response.hasError());
        assertEquals(404, response.getError().getCode());
    }
    
    // ==================== RESUME BOT TESTS ====================
    
    @Test
    @DisplayName("Should resume bot successfully")
    void testResumeBotSuccess() {
        // Given
        BotState pausedBot = createTestBotState(TEST_BOT_ID, false);
        pausedBot.setPositionStatus("PAUSED");
        when(botCacheService.getBotState(TEST_BOT_ID)).thenReturn(pausedBot);
        
        ResumeBotRequest request = ResumeBotRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .build();
        
        // When
        botManagementService.resumeBot(request, resumeBotResponseObserver);
        
        // Then
        ArgumentCaptor<ResumeBotResponse> responseCaptor = ArgumentCaptor.forClass(ResumeBotResponse.class);
        verify(resumeBotResponseObserver).onNext(responseCaptor.capture());
        verify(resumeBotResponseObserver).onCompleted();
        
        ResumeBotResponse response = responseCaptor.getValue();
        assertTrue(response.getSuccess());
        assertEquals("Bot resumed successfully", response.getMessage());
        assertEquals(tradingbot.grpc.common.BotState.RUNNING, response.getState());
        
        // Verify bot state was updated
        verify(botCacheService).saveBotState(eq(TEST_BOT_ID), any(BotState.class));
    }
    
    @Test
    @DisplayName("Should return error when resuming non-existent bot")
    void testResumeBotNotFound() {
        // Given
        when(botCacheService.getBotState(TEST_BOT_ID)).thenReturn(null);
        
        ResumeBotRequest request = ResumeBotRequest.newBuilder()
                .setBotId(TEST_BOT_ID)
                .build();
        
        // When
        botManagementService.resumeBot(request, resumeBotResponseObserver);
        
        // Then
        ArgumentCaptor<ResumeBotResponse> responseCaptor = ArgumentCaptor.forClass(ResumeBotResponse.class);
        verify(resumeBotResponseObserver).onNext(responseCaptor.capture());
        verify(resumeBotResponseObserver).onCompleted();
        
        ResumeBotResponse response = responseCaptor.getValue();
        assertFalse(response.getSuccess());
        assertEquals("Bot not found", response.getMessage());
        assertTrue(response.hasError());
        assertEquals(404, response.getError().getCode());
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Creates a test BotState instance
     */
    private BotState createTestBotState(String botId, boolean running) {
        TradingConfig config = new TradingConfig(
            TEST_SYMBOL,
            1000.0,
            3,
            2.0,
            14,
            30.0,
            70.0,
            12,
            26,
            9,
            20,
            2.0,
            900
        );
        
        return BotState.builder()
                .botId(botId)
                .paper(true)
                .running(running)
                .config(config)
                .sentimentEnabled(false)
                .currentLeverage(3)
                .createdAt(Instant.now())
                .lastUpdated(Instant.now())
                .positionStatus(running ? "RUNNING" : "STOPPED")
                .build();
    }
}
