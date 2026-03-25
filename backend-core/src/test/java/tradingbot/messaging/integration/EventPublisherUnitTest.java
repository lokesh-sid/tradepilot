package tradingbot.messaging.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import tradingbot.bot.TradeDirection;
import tradingbot.bot.events.RiskEvent;
import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.events.TradeSignalEvent;
import tradingbot.bot.messaging.EventPublisher;

/**
 * Unit test for EventPublisher with embedded Kafka.
 * This test focuses solely on the messaging functionality without loading
 * the entire Spring Boot application context.
 */
@SpringJUnitConfig(classes = AbstractEmbeddedKafkaTest.class)
@DisplayName("EventPublisher Unit Tests")
class EventPublisherUnitTest extends AbstractEmbeddedKafkaTest {

    @Autowired
    private EventPublisher eventPublisher;

    // Test data factory methods
    private TradeSignalEvent createTestSignalEvent(String botId, String symbol) {
        TradeSignalEvent event = new TradeSignalEvent(botId, symbol, TradeDirection.LONG);
        event.setStrength(0.8);
        return event;
    }

    private TradeExecutionEvent createTestExecutionEvent(String botId, String orderId, String symbol) {
        TradeExecutionEvent event = new TradeExecutionEvent(botId, orderId, symbol);
        event.setSide("BUY");
        event.setQuantity(1.0);
        event.setPrice(50000.0);
        event.setStatus("FILLED");
        return event;
    }

    private RiskEvent createTestRiskEvent(String botId, String riskType, String symbol) {
        RiskEvent event = new RiskEvent(botId, riskType, symbol);
        event.setSeverity("CRITICAL");
        event.setAction("CLOSE_POSITION");
        event.setDescription("Risk threshold exceeded");
        event.setCurrentPrice(45000.0);
        return event;
    }

    @Test
    @Timeout(30)
    @DisplayName("Should publish trade signal event successfully")
    void shouldPublishTradeSignalEvent() {
        // Given
        TradeSignalEvent event = createTestSignalEvent("unit-test-bot", "BTCUSDT");

        // When
        CompletableFuture<Void> result = eventPublisher.publishTradeSignal(event);
        
        // Then
        assertNotNull(result, "Publisher should return a non-null CompletableFuture");
        assertDoesNotThrow(() -> {
            result.get(15, TimeUnit.SECONDS);
        }, "Event publishing should complete without throwing exceptions");
    }

    @Test
    @Timeout(30)
    @DisplayName("Should publish trade execution event successfully")
    void shouldPublishTradeExecutionEvent() {
        // Given
        TradeExecutionEvent event = createTestExecutionEvent("unit-test-bot", "order-456", "ETHUSDT");
        event.setSide("SELL");
        event.setQuantity(0.5);
        event.setPrice(3000.0);

        // When
        CompletableFuture<Void> publishResult = eventPublisher.publishTradeExecution(event);

        // Then
        assertNotNull(publishResult, "Publisher should return a non-null CompletableFuture");
        assertDoesNotThrow(() -> {
            publishResult.get(15, TimeUnit.SECONDS);
        }, "Event publishing should complete without throwing exceptions");
    }

    @Test
    @Timeout(30)
    @DisplayName("Should publish risk event successfully")
    void shouldPublishRiskEvent() {
        // Given
        RiskEvent event = createTestRiskEvent("unit-test-bot", "STOP_LOSS_TRIGGERED", "BTCUSDT");
        event.setDescription("Stop loss triggered at 45000");

        // When
        CompletableFuture<Void> publishResult = eventPublisher.publishRiskEvent(event);

        // Then
        assertNotNull(publishResult, "Publisher should return a non-null CompletableFuture");
        assertDoesNotThrow(() -> {
            publishResult.get(15, TimeUnit.SECONDS);
        }, "Event publishing should complete without throwing exceptions");
    }

    @Test
    @Timeout(30)  
    @DisplayName("Should verify publisher health")
    void shouldVerifyPublisherHealth() {
        // When & Then
        assertTrue(eventPublisher.isHealthy(), "EventPublisher should be healthy");
    }

    // Negative test cases
    @Test
    @DisplayName("Should handle null trade signal event gracefully")
    void shouldHandleNullTradeSignalEvent() {
        // Given
        TradeSignalEvent nullEvent = null;
        
        // When
        CompletableFuture<Void> result = eventPublisher.publishTradeSignal(nullEvent);
        
        // Then - Should return a future that completes exceptionally
        assertNotNull(result, "Should return a CompletableFuture even for null event");
        assertThrows(Exception.class, () -> {
            result.get(5, TimeUnit.SECONDS);
        }, "Future should complete exceptionally for null event");
    }

    @Test
    @DisplayName("Should handle null trade execution event gracefully")
    void shouldHandleNullTradeExecutionEvent() {
        // Given
        TradeExecutionEvent nullEvent = null;
        
        // When
        CompletableFuture<Void> result = eventPublisher.publishTradeExecution(nullEvent);
        
        // Then - Should return a future that completes exceptionally
        assertNotNull(result, "Should return a CompletableFuture even for null event");
        assertThrows(Exception.class, () -> {
            result.get(5, TimeUnit.SECONDS);
        }, "Future should complete exceptionally for null event");
    }

    @Test
    @DisplayName("Should handle null risk event gracefully")
    void shouldHandleNullRiskEvent() {
        // Given
        RiskEvent nullEvent = null;
        
        // When
        CompletableFuture<Void> result = eventPublisher.publishRiskEvent(nullEvent);
        
        // Then - Should return a future that completes exceptionally
        assertNotNull(result, "Should return a CompletableFuture even for null event");
        assertThrows(Exception.class, () -> {
            result.get(5, TimeUnit.SECONDS);
        }, "Future should complete exceptionally for null event");
    }

    // Edge case tests
    @Test
    @Timeout(60)
    @DisplayName("Should handle rapid sequential publishing")
    void shouldHandleRapidSequentialPublishing() {
        // Given - Create 100 events
        List<CompletableFuture<Void>> futures = IntStream.range(0, 100)
            .mapToObj(i -> {
                TradeSignalEvent event = createTestSignalEvent("bot-" + i, "BTCUSDT");
                return eventPublisher.publishTradeSignal(event);
            })
            .toList();
        
        // Then - All should complete successfully
        assertDoesNotThrow(() -> {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);
        }, "All 100 events should publish successfully");
    }

    @Test
    @Timeout(30)
    @DisplayName("Should handle mixed event types concurrently")
    void shouldHandleMixedEventTypesConcurrently() {
        // Given - Mix of different event types
        List<CompletableFuture<Void>> futures = IntStream.range(0, 30)
            .mapToObj(i -> {
                switch (i % 3) {
                    case 0:
                        return eventPublisher.publishTradeSignal(
                            createTestSignalEvent("bot-" + i, "BTCUSDT"));
                    case 1:
                        return eventPublisher.publishTradeExecution(
                            createTestExecutionEvent("bot-" + i, "order-" + i, "ETHUSDT"));
                    default:
                        return eventPublisher.publishRiskEvent(
                            createTestRiskEvent("bot-" + i, "RISK_" + i, "BTCUSDT"));
                }
            })
            .toList();
        
        // Then - All should complete successfully
        assertDoesNotThrow(() -> {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(20, TimeUnit.SECONDS);
        }, "All mixed event types should publish successfully");
    }

    // Async behavior validation
    @Test
    @Timeout(30)
    @DisplayName("Should not block on publish")
    void shouldNotBlockOnPublish() {
        // Given
        TradeSignalEvent event = createTestSignalEvent("bot-123", "BTCUSDT");
        
        // When
        long startTime = System.currentTimeMillis();
        CompletableFuture<Void> result = eventPublisher.publishTradeSignal(event);
        long publishTime = System.currentTimeMillis() - startTime;
        
        // Then - publish should return immediately (< 100ms)
        assertTrue(publishTime < 100, 
            "Publish should be non-blocking, but took: " + publishTime + "ms");
        
        // But eventual completion should succeed
        assertDoesNotThrow(() -> result.get(15, TimeUnit.SECONDS),
            "Event should eventually complete successfully");
    }

    @Test
    @Timeout(30)
    @DisplayName("Should allow chaining of multiple publish operations")
    void shouldAllowChainingOfMultiplePublishOperations() {
        // Given
        TradeSignalEvent event1 = createTestSignalEvent("bot-1", "BTCUSDT");
        TradeSignalEvent event2 = createTestSignalEvent("bot-2", "ETHUSDT");
        TradeSignalEvent event3 = createTestSignalEvent("bot-3", "ADAUSDT");
        
        // When - Chain multiple publish operations
        CompletableFuture<Void> chainedResult = eventPublisher.publishTradeSignal(event1)
            .thenCompose(v -> eventPublisher.publishTradeSignal(event2))
            .thenCompose(v -> eventPublisher.publishTradeSignal(event3));
        
        // Then - Chained operations should complete successfully
        assertDoesNotThrow(() -> {
            chainedResult.get(20, TimeUnit.SECONDS);
        }, "Chained publish operations should complete successfully");
    }
}