package tradingbot.agent.config.factory;

import org.springframework.stereotype.Component;

import tradingbot.agent.config.ExchangeCredentials;
import tradingbot.agent.config.ExchangeServiceFactory;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.service.BinanceFuturesService;
import tradingbot.bot.service.FuturesExchangeService;

@Component
public class BinanceServiceFactory implements ExchangeServiceFactory {

    private final EventPublisher eventPublisher;

    public BinanceServiceFactory(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String exchangeName() {
        return "BINANCE";
    }

    @Override
    public FuturesExchangeService create(ExchangeCredentials creds) {
        if (creds == null) {
            throw new IllegalArgumentException("Missing credentials for BINANCE");
        }
        return new BinanceFuturesService(creds.getApiKey(), creds.getApiSecret(), eventPublisher);
    }
}
