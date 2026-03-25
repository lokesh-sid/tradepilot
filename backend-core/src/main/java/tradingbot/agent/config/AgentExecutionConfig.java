package tradingbot.agent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import tradingbot.agent.domain.execution.OrderExecutionGateway;
import tradingbot.agent.impl.execution.LiveOrderGateway;
import tradingbot.agent.impl.execution.PaperTradingOrderGateway;
import tradingbot.bot.service.FuturesExchangeService;

@Configuration
public class AgentExecutionConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "trading.execution.mode", havingValue = "paper", matchIfMissing = true)
    public OrderExecutionGateway paperTradingOrderGateway(FuturesExchangeService exchange) {
        return new PaperTradingOrderGateway(exchange, null);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "trading.execution.mode", havingValue = "live")
    public OrderExecutionGateway liveOrderGateway(FuturesExchangeService exchange) {
        return new LiveOrderGateway(exchange);
    }
}
