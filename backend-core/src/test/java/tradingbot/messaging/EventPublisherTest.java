package tradingbot.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import tradingbot.bot.events.BotStatusEvent;
import tradingbot.bot.events.MarketDataEvent;
import tradingbot.bot.events.RiskEvent;
import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.events.TradeSignalEvent;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.messaging.EventTopic;

/**
 * Unit tests for the Kafka-based EventPublisher.
 * 
 * Tests the publishing functionality without requiring a running Kafka cluster.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublisher Kafka Tests")
class EventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private CompletableFuture<SendResult<String, Object>> sendResultFuture;

    @Mock
    private org.springframework.kafka.core.ProducerFactory<String, Object> producerFactory;

    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new EventPublisher(kafkaTemplate);
        // Enable Kafka publishing (normally injected by @Value — not set in pure unit tests)
        ReflectionTestUtils.setField(eventPublisher, "kafkaPublishEnabled", true);
        
        // Mock successful Kafka send (lenient to avoid UnnecessaryStubbing in non-publishing tests)
        lenient().when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(sendResultFuture);
        
        // Mock the completion of sendResultFuture to simulate successful sending
        lenient().when(sendResultFuture.whenComplete(any())).thenReturn(sendResultFuture);
        
        // Mock producer factory for health checks and metrics (lenient to avoid UnnecessaryStubbing)
        lenient().when(kafkaTemplate.getProducerFactory()).thenReturn(producerFactory);
    }

    @Test
    @DisplayName("Should publish trade signal event to correct topic")
    void shouldPublishTradeSignalEvent() throws Exception {
        // Given
        TradeSignalEvent event = new TradeSignalEvent("bot-1", "BTCUSDT", tradingbot.bot.TradeDirection.LONG);
        event.setStrength(0.8);

        // When
        CompletableFuture<Void> result = eventPublisher.publishTradeSignal(event);

        // Then
        assertNotNull(result);
        
        // Wait for async operation to complete
        result.get(); // This will block until the async operation completes
        
        // Verify Kafka template was called with correct parameters
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<EventPublisher.EventWrapper> payloadCaptor = ArgumentCaptor.forClass(EventPublisher.EventWrapper.class);
        
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());
        
        assertEquals("trading.signals", topicCaptor.getValue());
        assertEquals("bot-1", keyCaptor.getValue());
        
        EventPublisher.EventWrapper wrapper = payloadCaptor.getValue();
        assertNotNull(wrapper);
        assertEquals(event.getEventId(), wrapper.getEventId());
        assertEquals("TradeSignalEvent", wrapper.getEventType());
        assertEquals("bot-1", wrapper.getPartitionKey());
        assertNotNull(wrapper.getPublishedAt());
        assertNotNull(wrapper.getData());
        assertEquals(event, wrapper.getData());
    }

    @Test
    @DisplayName("Should publish trade execution event to correct topic")
    void shouldPublishTradeExecutionEvent() throws Exception {
        // Given
        TradeExecutionEvent event = new TradeExecutionEvent("bot-1", "order-123", "BTCUSDT");
        event.setSide("BUY");
        event.setQuantity(0.01);
        event.setPrice(45000.0);
        event.setStatus("FILLED");

        // When
        CompletableFuture<Void> result = eventPublisher.publishTradeExecution(event);

        // Then
        assertNotNull(result);
        
        // Wait for async operation to complete
        result.get();
        
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), any());
        
        assertEquals("trading.executions", topicCaptor.getValue());
        assertEquals("bot-1", keyCaptor.getValue());
    }

    @Test
    @DisplayName("Should publish risk event to correct topic")
    void shouldPublishRiskEvent() throws Exception {
        // Given
        RiskEvent event = new RiskEvent("bot-1", "POSITION_SIZE_EXCEEDED", "BTCUSDT");
        event.setSeverity("HIGH");
        event.setAction("REDUCE_POSITION");
        event.setDescription("Position size exceeds risk limit");

        // When
        CompletableFuture<Void> result = eventPublisher.publishRiskEvent(event);

        // Then
        assertNotNull(result);
        
        // Wait for async operation to complete
        result.get();
        
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), any());
        
        assertEquals("trading.risk", topicCaptor.getValue());
        assertEquals("bot-1", keyCaptor.getValue());
    }

    @Test
    @DisplayName("Should publish market data event to correct topic")
    void shouldPublishMarketDataEvent() throws Exception {
        // Given
        MarketDataEvent event = new MarketDataEvent("test-bot", "BTCUSDT", 45000.0);
        event.setVolume(1000.0);

        // When
        CompletableFuture<Void> result = eventPublisher.publishMarketData(event);

        // Then
        assertNotNull(result);
        
        // Wait for async operation to complete
        result.get();
        
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), any());
        
        assertEquals("trading.market-data", topicCaptor.getValue());
        assertEquals("BTCUSDT", keyCaptor.getValue()); // Market data uses symbol as key
    }

    @Test
    @DisplayName("Should publish bot status event to correct topic")
    void shouldPublishBotStatusEvent() throws Exception {
        // Given
        BotStatusEvent event = new BotStatusEvent("bot-1", "STARTED");
        event.setMessage("Bot started successfully");
        event.setRunning(true);
        event.setCurrentBalance(1000.0);

        // When
        CompletableFuture<Void> result = eventPublisher.publishBotStatus(event);

        // Then
        assertNotNull(result);
        
        // Wait for async operation to complete
        result.get();
        
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), any());
        
        assertEquals("trading.bot-status", topicCaptor.getValue());
        assertEquals("bot-1", keyCaptor.getValue());
    }

    @Test
    @DisplayName("Should handle Kafka publishing failure gracefully")
    void shouldHandleKafkaPublishingFailure() throws Exception {
        // Given
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka connection failed"));
        
        // Override the mock for this specific test (use doReturn to avoid stubbing conflicts)
        doReturn(failedFuture).when(kafkaTemplate).send(anyString(), anyString(), any());

        TradeSignalEvent event = new TradeSignalEvent("bot-1", "BTCUSDT", tradingbot.bot.TradeDirection.LONG);

        // When & Then
        CompletableFuture<Void> result = eventPublisher.publishTradeSignal(event);
        assertNotNull(result);
        
        // The async task should complete, but may throw EventPublishingException internally
        // We don't expect it to propagate to the result future in this case
        assertDoesNotThrow(() -> result.get());
    }

    @Test
    @DisplayName("Should return negative count for topic event count (Kafka limitation)")
    void shouldReturnNegativeCountForTopicEventCount() {
        // When
        long count = eventPublisher.getTopicEventCount(EventTopic.TRADE_SIGNALS);

        // Then
        assertEquals(-1, count); // Kafka doesn't provide direct count
    }

    @Test
    @DisplayName("Should return publisher metrics")
    void shouldReturnPublisherMetrics() {
        // When
        Map<String, Object> metrics = eventPublisher.getPublisherMetrics();

        // Then
        assertNotNull(metrics);
        assertEquals("Kafka", metrics.get("producer-type"));
        assertNotNull(metrics.get("timestamp"));
        assertEquals("active", metrics.get("status"));
    }

    @Test
    @DisplayName("Should return healthy status when Kafka template is available")
    void shouldReturnHealthyStatus() {
        // When
        boolean healthy = eventPublisher.isHealthy();

        // Then
        assertTrue(healthy);
    }

    @Test
    @DisplayName("Should handle health check failure gracefully")
    void shouldHandleHealthCheckFailure() {
        // Given
        when(kafkaTemplate.getProducerFactory())
            .thenThrow(new RuntimeException("Connection failed"));

        // When
        boolean healthy = eventPublisher.isHealthy();

        // Then
        assertFalse(healthy);
    }
}