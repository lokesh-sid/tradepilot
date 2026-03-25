package tradingbot.agent.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import tradingbot.bot.service.FuturesExchangeService;

/**
 * Registry that resolves a named exchange to its {@link FuturesExchangeService} instance.
 *
 * <p>Instances are created lazily on first use and cached for reuse.
 * A null or blank exchange name falls back to the globally configured {@code @Primary} bean.
 *
 * <p>Exchange support is open for extension: adding a new exchange requires only
 * a new {@link ExchangeServiceFactory} {@code @Component} — this class never changes.
 * Spring injects all factory implementations automatically via {@code List} injection.
 *
 * <p>This is the single authoritative place for exchange instantiation — both
 * {@code AgentFactory} and the LLM execution path ({@code TradingTools} /
 * {@code OrderPlacementService}) delegate here so the creation logic is not duplicated.
 */
@Component
public class ExchangeServiceRegistry {

    private final Map<String, ExchangeServiceFactory> factories;
    private final AgentProperties agentProperties;
    private final FuturesExchangeService primaryExchangeService;
    private final Map<String, FuturesExchangeService> cache = new ConcurrentHashMap<>();

    public ExchangeServiceRegistry(
            List<ExchangeServiceFactory> factories,
            AgentProperties agentProperties,
            FuturesExchangeService primaryExchangeService) {
        this.factories = factories.stream()
                .collect(Collectors.toUnmodifiableMap(
                        f -> f.exchangeName().toUpperCase(),
                        f -> f));
        this.agentProperties = agentProperties;
        this.primaryExchangeService = primaryExchangeService;
    }

    /**
     * Resolve the {@link FuturesExchangeService} for the given exchange name.
     * Returns the {@code @Primary} bean when {@code exchangeName} is null or blank.
     */
    public FuturesExchangeService resolve(String exchangeName) {
        if (exchangeName == null || exchangeName.isBlank()) {
            return primaryExchangeService;
        }
        return cache.computeIfAbsent(exchangeName.toLowerCase(), this::create);
    }

    private FuturesExchangeService create(String exchangeKey) {
        String exchange = exchangeKey.toUpperCase();
        ExchangeServiceFactory factory = factories.get(exchange);
        if (factory == null) {
            throw new IllegalArgumentException("Unsupported exchange: " + exchange
                    + ". Registered exchanges: " + factories.keySet());
        }
        Map<String, ExchangeCredentials> credentials = agentProperties.getCredentials();
        ExchangeCredentials creds = credentials != null ? credentials.get(exchangeKey) : null;
        return factory.create(creds);
    }
}
