package tradingbot.agent.config.factory;

import org.springframework.stereotype.Component;

import tradingbot.agent.config.ExchangeCredentials;
import tradingbot.agent.config.ExchangeServiceFactory;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.service.DydxFuturesService;
import tradingbot.bot.service.FuturesExchangeService;

@Component
public class DydxServiceFactory implements ExchangeServiceFactory {

    private final EventPublisher eventPublisher;

    public DydxServiceFactory(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String exchangeName() {
        return "DYDX";
    }

    @Override
    public FuturesExchangeService create(ExchangeCredentials creds) {
        if (creds == null) {
            throw new IllegalArgumentException("Missing credentials for DYDX");
        }
        return new DydxFuturesService(
                creds.getNetwork(), creds.getMainnetUrl(), creds.getTestnetUrl(),
                creds.getPrivateKey(), eventPublisher);
    }
}
