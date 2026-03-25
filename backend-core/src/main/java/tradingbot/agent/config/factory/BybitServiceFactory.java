package tradingbot.agent.config.factory;

import org.springframework.stereotype.Component;

import tradingbot.agent.config.ExchangeCredentials;
import tradingbot.agent.config.ExchangeServiceFactory;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.service.BybitFuturesService;
import tradingbot.bot.service.FuturesExchangeService;

@Component
public class BybitServiceFactory implements ExchangeServiceFactory {

    private final EventPublisher eventPublisher;

    public BybitServiceFactory(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String exchangeName() {
        return "BYBIT";
    }

    @Override
    public FuturesExchangeService create(ExchangeCredentials creds) {
        if (creds == null) {
            throw new IllegalArgumentException("Missing credentials for BYBIT");
        }
        String baseUrl = "TESTNET_DOMAIN".equalsIgnoreCase(creds.getDomain())
                ? "https://api-testnet.bybit.com"
                : "https://api.bybit.com";
        return new BybitFuturesService(creds.getApiKey(), creds.getApiSecret(), baseUrl, eventPublisher);
    }
}
