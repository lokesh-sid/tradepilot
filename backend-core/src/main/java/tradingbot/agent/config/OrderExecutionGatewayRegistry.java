package tradingbot.agent.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tradingbot.agent.domain.execution.OrderExecutionGateway;
import tradingbot.agent.impl.execution.LiveOrderGateway;
import tradingbot.agent.impl.execution.PaperTradingOrderGateway;
import tradingbot.bot.service.FuturesExchangeService;

/**
 * Registry that provides an {@link OrderExecutionGateway} per agent.
 *
 * <p>The cache key is {@code agentId}, not {@code exchangeName}, for two reasons:
 * <ol>
 *   <li><b>Exchange routing</b>: each agent may target a different exchange;
 *       the gateway is backed by that agent's {@link FuturesExchangeService}.</li>
 *   <li><b>Position state isolation</b>: {@link LiveOrderGateway} tracks open
 *       positions per symbol in internal maps. If two agents on the same exchange
 *       shared a gateway, their position state would collide — each would see the
 *       other's positions as its own.</li>
 * </ol>
 *
 * <p>Gateways are created lazily on first use. The execution mode
 * ({@code trading.execution.mode}) is read once at startup and applied uniformly
 * across all agents — mixed live/paper per-agent is not supported here.
 */
@Component
public class OrderExecutionGatewayRegistry {

    private static final Logger log = LoggerFactory.getLogger(OrderExecutionGatewayRegistry.class);

    private final ExchangeServiceRegistry exchangeServiceRegistry;
    private final boolean liveMode;

    private final Map<String, OrderExecutionGateway> cache = new ConcurrentHashMap<>();

    public OrderExecutionGatewayRegistry(
            ExchangeServiceRegistry exchangeServiceRegistry,
            @Value("${trading.execution.mode:paper}") String executionMode) {
        this.exchangeServiceRegistry = exchangeServiceRegistry;
        this.liveMode = "live".equalsIgnoreCase(executionMode);
        log.info("OrderExecutionGatewayRegistry initialized in {} mode", liveMode ? "LIVE" : "PAPER");
    }

    /**
     * Returns the gateway for the given agent, creating and caching it on first call.
     *
     * @param agentId      the agent's identifier — used as the cache key
     * @param exchangeName the agent's configured exchange; null/blank falls back
     *                     to the {@code @Primary} exchange service
     */
    public OrderExecutionGateway resolve(String agentId, String exchangeName) {
        return cache.computeIfAbsent(agentId, id -> create(id, exchangeName));
    }

    /**
     * Evicts the cached gateway for an agent that has been stopped or deleted.
     * Should be called when an agent is deregistered so stale state is not retained.
     */
    public void evict(String agentId) {
        cache.remove(agentId);
        log.debug("Evicted gateway for agent {}", agentId);
    }

    private OrderExecutionGateway create(String agentId, String exchangeName) {
        FuturesExchangeService exchange = exchangeServiceRegistry.resolve(exchangeName);
        OrderExecutionGateway gateway = liveMode
                ? new LiveOrderGateway(exchange)
                : new PaperTradingOrderGateway(exchange, null);
        log.info("Created {} gateway for agent {} on exchange {}",
                liveMode ? "LIVE" : "PAPER", agentId,
                exchangeName != null && !exchangeName.isBlank() ? exchangeName : "primary");
        return gateway;
    }
}
