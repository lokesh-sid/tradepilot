package tradingbot.agent.api.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tradingbot.agent.api.dto.OrderResponse;
import tradingbot.agent.application.OrderService;
import tradingbot.agent.infrastructure.persistence.OrderEntity;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Get order by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable String id) {
        log.info("Received GET /api/v1/orders/{}", id);
        ResponseEntity<OrderResponse> response = orderService.getOrderById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
        log.info("Response for GET /api/v1/orders/{}: {}", id, response.getStatusCode());
        return response;
    }

    /**
     * Create a new order
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody OrderEntity order,
            Authentication authentication) {
        log.info("Received POST /api/v1/orders with payload: {}", order);
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Anonymous order request received");
            // Optionally reject anonymous requests:
            // return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        OrderResponse response = orderService.createOrder(order);
        log.info("Response for POST /api/v1/orders: {}", response);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Update an order
     */
    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> updateOrder(@PathVariable String id,
                                                    @RequestBody OrderEntity updatedOrder) {
        log.info("Received PUT /api/v1/orders/{} with payload: {}", id, updatedOrder);
        ResponseEntity<OrderResponse> response = orderService.updateOrder(id, updatedOrder)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
        log.info("Response for PUT /api/v1/orders/{}: {}", id, response.getStatusCode());
        return response;
    }

    /**
     * Delete an order
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable String id) {
        log.info("Received DELETE /api/v1/orders/{}", id);
        boolean deleted = orderService.deleteOrder(id);
        ResponseEntity<Void> response = deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
        log.info("Response for DELETE /api/v1/orders/{}: {}", id, response.getStatusCode());
        return response;
    }

    /**
     * Fetch orders for a given agent (or all orders if agentId not provided)
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrders(
            @RequestParam(required = false) String agentId) {
        log.info("Received GET /api/v1/orders with agentId={}", agentId);
        List<OrderResponse> orders = orderService.getOrders(agentId);
        log.info("Response for GET /api/v1/orders: {} orders", orders.size());
        return ResponseEntity.ok(orders);
    }
}
