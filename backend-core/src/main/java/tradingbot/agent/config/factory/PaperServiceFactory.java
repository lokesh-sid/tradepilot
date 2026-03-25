package tradingbot.agent.config.factory;

import org.springframework.stereotype.Component;

import tradingbot.agent.config.ExchangeCredentials;
import tradingbot.agent.config.ExchangeServiceFactory;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.PaperFuturesExchangeService;

@Component
public class PaperServiceFactory implements ExchangeServiceFactory {

    @Override
    public String exchangeName() {
        return "PAPER";
    }

    @Override
    public FuturesExchangeService create(ExchangeCredentials creds) {
        return new PaperFuturesExchangeService();
    }
}
