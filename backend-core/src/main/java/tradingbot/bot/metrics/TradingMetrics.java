package tradingbot.bot.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tradingbot.agent.manager.AgentManager;

/**
 * Custom Micrometer metrics for the Trading Bot, exposed at {@code /actuator/prometheus}.
 *
 * <ul>
 *   <li>{@code trading_orders_entered_total} – counter per symbol / direction</li>
 *   <li>{@code trading_orders_exited_total}  – counter per symbol / direction</li>
 *   <li>{@code trading_events_published_total} – counter per Kafka event type</li>
 *   <li>{@code trading_order_placement_seconds} – timer for order-placement latency</li>
 *   <li>{@code trading_bots_registered}  – gauge: total registered bots</li>
 *   <li>{@code trading_bots_running}     – gauge: currently running bots</li>
 * </ul>
 */
@Component
public class TradingMetrics {

    private static final String SYMBOL    = "symbol";
    private static final String DIRECTION = "direction";
    private static final String EVENT_TYPE = "eventType";

    private final MeterRegistry meterRegistry;

    /**
     * {@code ObjectProvider} is used instead of direct injection to break the
     * circular dependency:
     * AgentManager → AgentFactory → EventPublisher → TradingMetrics → AgentManager.
     * The provider is resolved lazily when the Gauge sampler executes.
     */
    public TradingMetrics(MeterRegistry meterRegistry, ObjectProvider<AgentManager> agentManagerProvider) {
        this.meterRegistry = meterRegistry;

        Gauge.builder("trading.bots.registered",
                      agentManagerProvider, provider -> {
                          AgentManager am = provider.getIfAvailable();
                          return am != null ? am.getAgents().size() : 0;
                      })
             .description("Total number of registered trading bots")
             .register(meterRegistry);

        Gauge.builder("trading.bots.running",
                      agentManagerProvider, provider -> {
                          AgentManager am = provider.getIfAvailable();
                          return am != null ? am.getAgents().stream()
                                               .filter(a -> a.isRunning())
                                               .count() : 0;
                      })
             .description("Number of currently running trading bots")
             .register(meterRegistry);
    }

    /**
     * Records a trade-entry order (long or short position opened).
     *
     * @param symbol    trading symbol, e.g. {@code BTCUSDT}
     * @param direction {@code LONG} or {@code SHORT}
     */
    public void recordOrderEntered(String symbol, String direction) {
        Counter.builder("trading.orders.entered")
               .description("Total trading orders entered by symbol and direction")
               .tag(SYMBOL, symbol)
               .tag(DIRECTION, direction)
               .register(meterRegistry)
               .increment();
    }

    /**
     * Records a trade-exit order (long or short position closed).
     *
     * @param symbol    trading symbol
     * @param direction {@code LONG} or {@code SHORT}
     */
    public void recordOrderExited(String symbol, String direction) {
        Counter.builder("trading.orders.exited")
               .description("Total trading orders exited by symbol and direction")
               .tag(SYMBOL, symbol)
               .tag(DIRECTION, direction)
               .register(meterRegistry)
               .increment();
    }

    /**
     * Records a Kafka event published by {@link tradingbot.bot.messaging.EventPublisher}.
     *
     * @param eventType simple class name of the event, e.g. {@code TradeSignalEvent}
     */
    public void recordEventPublished(String eventType) {
        Counter.builder("trading.events.published")
               .description("Total trading events published to Kafka")
               .tag(EVENT_TYPE, eventType)
               .register(meterRegistry)
               .increment();
    }

    /**
     * Returns a {@link Timer} for measuring order-placement latency.
     *
     * <p>Usage:
     * <pre>{@code
     * tradingMetrics.orderPlacementTimer(symbol, direction).record(() -> placeOrder(...));
     * }</pre>
     *
     * @param symbol    trading symbol
     * @param direction {@code LONG} or {@code SHORT}
     */
    public Timer orderPlacementTimer(String symbol, String direction) {
        return Timer.builder("trading.order.placement")
                    .description("Latency of order placement operations")
                    .tag(SYMBOL, symbol)
                    .tag(DIRECTION, direction)
                    .register(meterRegistry);
    }
}
