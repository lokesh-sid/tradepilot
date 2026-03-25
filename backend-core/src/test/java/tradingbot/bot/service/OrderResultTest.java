package tradingbot.bot.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for OrderResult DTO
 */
class OrderResultTest {

    @Test
    @DisplayName("Builder creates OrderResult with all fields")
    void testBuilderWithAllFields() {
        Instant now = Instant.now();
        
        OrderResult result = OrderResult.builder()
            .exchangeOrderId("12345678")
            .clientOrderId("CLIENT-123")
            .symbol("BTCUSDT")
            .side("BUY")
            .status(OrderResult.OrderStatus.FILLED)
            .orderedQuantity(0.1)
            .filledQuantity(0.1)
            .avgFillPrice(50000.0)
            .commission(5.0)
            .createdAt(now)
            .updatedAt(now)
            .build();
        
        assertEquals("12345678", result.getExchangeOrderId());
        assertEquals("CLIENT-123", result.getClientOrderId());
        assertEquals("BTCUSDT", result.getSymbol());
        assertEquals("BUY", result.getSide());
        assertEquals(OrderResult.OrderStatus.FILLED, result.getStatus());
        assertEquals(0.1, result.getOrderedQuantity(), 0.001);
        assertEquals(0.1, result.getFilledQuantity(), 0.001);
        assertEquals(50000.0, result.getAvgFillPrice(), 0.01);
        assertEquals(5.0, result.getCommission(), 0.01);
        assertEquals(now, result.getCreatedAt());
        assertEquals(now, result.getUpdatedAt());
    }

    @Test
    @DisplayName("Builder creates OrderResult with minimal fields")
    void testBuilderWithMinimalFields() {
        OrderResult result = OrderResult.builder()
            .exchangeOrderId("12345678")
            .symbol("BTCUSDT")
            .side("SELL")
            .status(OrderResult.OrderStatus.NEW)
            .orderedQuantity(0.05)
            .filledQuantity(0.0)
            .avgFillPrice(0.0)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        
        assertNotNull(result);
        assertEquals("12345678", result.getExchangeOrderId());
        assertNull(result.getClientOrderId());
    }

    @Test
    @DisplayName("OrderStatus enum has all required states")
    void testOrderStatusEnum() {
        assertEquals(6, OrderResult.OrderStatus.values().length);
        
        assertTrue(OrderResult.OrderStatus.NEW instanceof OrderResult.OrderStatus);
        assertTrue(OrderResult.OrderStatus.PARTIALLY_FILLED instanceof OrderResult.OrderStatus);
        assertTrue(OrderResult.OrderStatus.FILLED instanceof OrderResult.OrderStatus);
        assertTrue(OrderResult.OrderStatus.CANCELED instanceof OrderResult.OrderStatus);
        assertTrue(OrderResult.OrderStatus.REJECTED instanceof OrderResult.OrderStatus);
        assertTrue(OrderResult.OrderStatus.EXPIRED instanceof OrderResult.OrderStatus);
    }

    @Test
    @DisplayName("Partially filled order has correct quantities")
    void testPartiallyFilledOrder() {
        OrderResult result = OrderResult.builder()
            .exchangeOrderId("12345678")
            .symbol("ETHUSDT")
            .side("BUY")
            .status(OrderResult.OrderStatus.PARTIALLY_FILLED)
            .orderedQuantity(1.0)
            .filledQuantity(0.5)
            .avgFillPrice(3000.0)
            .commission(1.5)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        
        assertEquals(1.0, result.getOrderedQuantity(), 0.001);
        assertEquals(0.5, result.getFilledQuantity(), 0.001);
        assertTrue(result.getFilledQuantity() < result.getOrderedQuantity());
        assertEquals(OrderResult.OrderStatus.PARTIALLY_FILLED, result.getStatus());
    }

    @Test
    @DisplayName("Canceled order has zero filled quantity")
    void testCanceledOrder() {
        OrderResult result = OrderResult.builder()
            .exchangeOrderId("12345678")
            .symbol("BTCUSDT")
            .side("SELL")
            .status(OrderResult.OrderStatus.CANCELED)
            .orderedQuantity(0.1)
            .filledQuantity(0.0)
            .avgFillPrice(0.0)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        
        assertEquals(OrderResult.OrderStatus.CANCELED, result.getStatus());
        assertEquals(0.0, result.getFilledQuantity(), 0.001);
        assertEquals(0.0, result.getCommission(), 0.01);
    }

    @Test
    @DisplayName("Filled order has complete fill")
    void testFilledOrder() {
        OrderResult result = OrderResult.builder()
            .exchangeOrderId("87654321")
            .symbol("SOLUSDT")
            .side("BUY")
            .status(OrderResult.OrderStatus.FILLED)
            .orderedQuantity(10.0)
            .filledQuantity(10.0)
            .avgFillPrice(100.0)
            .commission(1.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        
        assertEquals(OrderResult.OrderStatus.FILLED, result.getStatus());
        assertEquals(result.getOrderedQuantity(), result.getFilledQuantity(), 0.001);
        assertTrue(result.getAvgFillPrice() > 0);
        assertTrue(result.getCommission() > 0);
    }

    @Test
    @DisplayName("Stop-loss order structure")
    void testStopLossOrder() {
        OrderResult result = OrderResult.builder()
            .exchangeOrderId("SL-123456")
            .clientOrderId("SL-CLIENT-123")
            .symbol("BTCUSDT")
            .side("SELL")
            .status(OrderResult.OrderStatus.NEW)
            .orderedQuantity(0.1)
            .filledQuantity(0.0)
            .avgFillPrice(0.0)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        
        assertTrue(result.getExchangeOrderId().startsWith("SL-"));
        assertTrue(result.getClientOrderId().startsWith("SL-"));
        assertEquals(OrderResult.OrderStatus.NEW, result.getStatus());
    }

    @Test
    @DisplayName("Take-profit order structure")
    void testTakeProfitOrder() {
        OrderResult result = OrderResult.builder()
            .exchangeOrderId("TP-789012")
            .clientOrderId("TP-CLIENT-456")
            .symbol("ETHUSDT")
            .side("BUY")
            .status(OrderResult.OrderStatus.NEW)
            .orderedQuantity(1.0)
            .filledQuantity(0.0)
            .avgFillPrice(0.0)
            .commission(0.0)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        
        assertTrue(result.getExchangeOrderId().startsWith("TP-"));
        assertTrue(result.getClientOrderId().startsWith("TP-"));
        assertEquals(OrderResult.OrderStatus.NEW, result.getStatus());
    }
}
