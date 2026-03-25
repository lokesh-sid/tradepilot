package tradingbot.agent.config;

import tradingbot.bot.service.FuturesExchangeService;

/**
 * Factory for creating a {@link FuturesExchangeService} for a specific exchange.
 *
 * Implementations are Spring {@code @Component}s. {@link ExchangeServiceRegistry}
 * auto-discovers all registered factories via Spring's {@code List} injection —
 * adding support for a new exchange requires only a new implementation class,
 * with no changes to the registry.
 *
 * Each implementation is responsible for validating its own credentials.
 */
public interface ExchangeServiceFactory {

    /**
     * The canonical upper-case name for this exchange, e.g. {@code "BINANCE"}.
     * Must match the value used in agent configuration (case-insensitive comparison
     * is handled by the registry).
     */
    String exchangeName();

    /**
     * Create a new service instance using the provided credentials.
     *
     * @param creds credentials from agent config; may be {@code null} for
     *              exchanges that do not require authentication (e.g. PAPER)
     * @throws IllegalArgumentException if required credentials are missing
     */
    FuturesExchangeService create(ExchangeCredentials creds);
}
