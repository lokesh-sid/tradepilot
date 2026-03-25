package tradingbot.bot.controller.config;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import net.devh.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration;
import net.devh.boot.grpc.server.autoconfigure.GrpcServerFactoryAutoConfiguration;
import tradingbot.agent.api.controller.AgentController;
import tradingbot.bot.controller.TradingBotController;
import tradingbot.bot.controller.exception.GlobalExceptionHandler;
import tradingbot.bot.controller.validation.BotOperationPolicy;
import tradingbot.bot.controller.validation.BotRequestValidator;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
    KafkaAutoConfiguration.class,
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class,
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    GrpcServerAutoConfiguration.class,
    GrpcServerFactoryAutoConfiguration.class
})
@ComponentScan(
    basePackages = {"tradingbot.bot.controller", "tradingbot.agent.api"},
    useDefaultFilters = false,
    includeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {
                TradingBotController.class,
                GlobalExceptionHandler.class,
                AgentController.class,
                BotRequestValidator.class,
                BotOperationPolicy.class
            }
        )
    }
)
public class TradingBotControllerValidationTestConfig {
}
