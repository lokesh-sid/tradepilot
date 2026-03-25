package tradingbot.agent.api.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import tradingbot.agent.api.dto.OrderResponse;
import tradingbot.agent.application.OrderService;
import tradingbot.agent.infrastructure.persistence.OrderEntity;

public class OrderControllerTest {
    private OrderService orderService;
    private OrderController orderController;

    @BeforeEach
    void setUp() {
        orderService = Mockito.mock(OrderService.class);
        orderController = new OrderController(orderService);
    }

    @Test
    void testCreateOrderViaApi() {
        OrderEntity order = OrderEntity.builder()
            .agentId("AGENT1234")
            .symbol("BTCUSDT")
            .direction(OrderEntity.Direction.LONG)
            .price(50000.0)
            .quantity(0.1)
            .status(OrderEntity.Status.PENDING)
            .createdAt(Instant.now())
            .build();

        OrderResponse mockResponse = new OrderResponse();
        mockResponse.id = "AGENT1234-20260319T153045Z-1a2b3c4d";
        mockResponse.agentId = "AGENT1234";
        mockResponse.symbol = "BTCUSDT";
        mockResponse.direction = "LONG";
        mockResponse.price = 50000.0;
        mockResponse.quantity = 0.1;
        mockResponse.status = "PENDING";
        mockResponse.createdAt = order.getCreatedAt();

        Mockito.when(orderService.createOrder(Mockito.any(OrderEntity.class))).thenReturn(mockResponse);

        ResponseEntity<OrderResponse> response = orderController.createOrder(order, null);
        assertEquals(201, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().id.startsWith("AGENT1234-"));
        assertEquals("BTCUSDT", response.getBody().symbol);
    }
}
