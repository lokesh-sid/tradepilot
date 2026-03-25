package tradingbot.agent.application;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import tradingbot.agent.api.dto.OrderResponse;
import tradingbot.agent.infrastructure.persistence.OrderEntity;
import tradingbot.agent.infrastructure.repository.OrderRepository;

public class OrderServiceTest {
    private OrderRepository orderRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = Mockito.mock(OrderRepository.class);
        orderService = new OrderService(orderRepository);
    }

    @Test
    void testCreateOrderGeneratesCompositeId() {
        OrderEntity order = OrderEntity.builder()
            .agentId("AGENT1234")
            .symbol("BTCUSDT")
            .direction(OrderEntity.Direction.LONG)
            .price(50000.0)
            .quantity(0.1)
            .status(OrderEntity.Status.PENDING)
            .createdAt(Instant.now())
            .build();

        Mockito.when(orderRepository.save(Mockito.any(OrderEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(order);
        assertNotNull(response.id);
        assertTrue(response.id.startsWith("AGENT1234-"));
        assertTrue(response.id.length() > "AGENT1234-".length());
    }
}
