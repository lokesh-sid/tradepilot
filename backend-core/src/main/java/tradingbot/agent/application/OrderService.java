package tradingbot.agent.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tradingbot.agent.api.dto.OrderResponse;
import tradingbot.agent.domain.util.OrderIdGenerator;
import tradingbot.agent.infrastructure.persistence.OrderEntity;
import tradingbot.agent.infrastructure.repository.OrderRepository;

@Service
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<OrderResponse> getOrders(String agentId) {
        List<OrderEntity> entities = (agentId != null && !agentId.isEmpty())
            ? orderRepository.findByAgentId(agentId)
            : orderRepository.findAll();
        return entities.stream().map(OrderEntity::toOrderResponse).toList();
    }

    public Optional<OrderResponse> getOrderById(String id) {
        return orderRepository.findById(id).map(OrderEntity::toOrderResponse);
    }

    public OrderResponse createOrder(OrderEntity order) {
        // Build a new OrderEntity to ensure all required fields are set
        OrderEntity newOrder = OrderEntity.builder()
            .id(order.getId() == null || order.getId().isBlank() ? OrderIdGenerator.forAgent(order.getAgentId()) : order.getId())
            .agentId(order.getAgentId())
            .symbol(order.getSymbol())
            .direction(order.getDirection())
            .price(order.getPrice())
            .quantity(order.getQuantity())
            .status(order.getStatus())
            .createdAt(order.getCreatedAt() != null ? order.getCreatedAt() : Instant.now())
            .stopLoss(order.getStopLoss())
            .takeProfit(order.getTakeProfit())
            .leverage(order.getLeverage())
            .executedAt(order.getExecutedAt())
            .exchangeOrderId(order.getExchangeOrderId())
            .failureReason(order.getFailureReason())
            .realizedPnl(order.getRealizedPnl())
            .build();
        OrderEntity saved = orderRepository.save(newOrder);
        return saved.toOrderResponse();
    }

    public Optional<OrderResponse> updateOrder(String id, OrderEntity updatedOrder) {
        return orderRepository.findById(id).map(existing -> {
            updatedOrder.setId(id);
            OrderEntity saved = orderRepository.save(updatedOrder);
            return saved.toOrderResponse();
        });
    }

    public boolean deleteOrder(String id) {
        if (orderRepository.existsById(id)) {
            orderRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
