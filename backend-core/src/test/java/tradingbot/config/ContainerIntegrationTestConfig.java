package tradingbot.config;

import java.util.List;

import javax.sql.DataSource;

import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import reactor.core.publisher.Flux;
import tradingbot.agent.TradingAgentFactory;
import tradingbot.agent.config.AgentExecutionContext;
import tradingbot.agent.config.ExchangeServiceRegistry;
import tradingbot.agent.config.OrderExecutionGatewayRegistry;
import tradingbot.agent.factory.AgentFactory;
import tradingbot.agent.infrastructure.llm.LLMProvider;
import tradingbot.agent.infrastructure.persistence.PositionEntity;
import tradingbot.agent.infrastructure.repository.AgentEntity;
import tradingbot.agent.manager.AgentManager;
import tradingbot.agent.service.OrderPlacementService;
import tradingbot.agent.service.RAGService;
import tradingbot.agent.service.TradeReflectionService;
import tradingbot.agent.service.TradingAgentService;
import tradingbot.agent.service.TradingTools;
import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.controller.dto.BotState;
import tradingbot.bot.persistence.entity.TradingEventEntity;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.PaperFuturesExchangeService;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.bot.strategy.calculator.IndicatorCalculator;
import tradingbot.bot.strategy.calculator.IndicatorValues;
import tradingbot.bot.strategy.exit.PositionExitCondition;
import tradingbot.bot.strategy.tracker.TrailingStopTracker;
import tradingbot.domain.market.StreamMarketDataEvent;
import tradingbot.infrastructure.marketdata.ExchangeWebSocketClient;
import tradingbot.security.repository.RefreshTokenRepository;
import tradingbot.security.repository.UserRepository;

/**
 * Spring test configuration for Testcontainers-based integration tests.
 *
 * <p>Mirrors {@link FuturesTradingBotIntegrationTestConfig} but intentionally omits
 * the {@code dataSource()} bean so that Spring Boot auto-configuration creates a
 * real {@link javax.sql.DataSource} from the PostgreSQL URL injected by
 * {@link tradingbot.AbstractContainerIntegrationTest#configureContainerProperties}.
 *
 * <p>Kafka auto-configuration is also kept enabled so the real Kafka container
 * bootstrapped by Testcontainers is used.
 */
@SpringBootConfiguration
@TestPropertySource(
    locations = "classpath:application-container-test.properties",
    properties = {
        "management.endpoints.web.exposure.include=*",
        "management.endpoint.prometheus.enabled=true",
        "management.health.redis.enabled=false",
        "management.health.kafka.enabled=false"
    }
)
@EnableAutoConfiguration(exclude = {
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class,
    SecurityAutoConfiguration.class,
    ManagementWebSecurityAutoConfiguration.class,
    net.devh.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration.class,
    net.devh.boot.grpc.server.autoconfigure.GrpcServerFactoryAutoConfiguration.class,
    net.devh.boot.grpc.server.autoconfigure.GrpcServerMetricAutoConfiguration.class,
    net.devh.boot.grpc.server.autoconfigure.GrpcServerSecurityAutoConfiguration.class,
    net.devh.boot.grpc.server.autoconfigure.GrpcHealthServiceAutoConfiguration.class
    // KafkaAutoConfiguration is intentionally NOT excluded – real Kafka from Testcontainer
})
@ComponentScan(
    basePackages = {
        "tradingbot.bot.controller",
        "tradingbot.bot.service",
        "tradingbot.bot.messaging",
        "tradingbot.bot.metrics",
        "tradingbot.bot.persistence.service",
        "tradingbot.agent.application",
        "tradingbot.agent.impl",
        "tradingbot.agent.infrastructure.repository",
        "tradingbot.agent.api",
        "tradingbot.security.controller",
        "tradingbot.security.config",
        "tradingbot.security.filter",
        "tradingbot.security.service"
    },
    useDefaultFilters = true,
    excludeFilters = {
        @Filter(type = FilterType.ANNOTATION, classes = EnableAutoConfiguration.class)
    }
)
@Import({InstanceConfig.class, KafkaConfig.class, AgentManager.class, AgentFactory.class})
@EnableJpaRepositories(basePackages = {
    "tradingbot.agent.persistence",
    "tradingbot.agent.infrastructure.repository",
    "tradingbot.bot.persistence.repository"
})
@EntityScan(basePackageClasses = {
    PositionEntity.class,
    TradingEventEntity.class,
    AgentEntity.class
})
public class ContainerIntegrationTestConfig {

    @Bean
    public tradingbot.agent.config.AgentProperties agentProperties() {
        return new tradingbot.agent.config.AgentProperties();
    }

    /**
     * Builds a PostgreSQL datasource from properties injected by
     * {@link tradingbot.AbstractContainerIntegrationTest#configureContainerProperties}.
     */
    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    @Bean
    public TradingSafetyService tradingSafetyService() {
        return new TradingSafetyService("paper", "paper", "TESTNET_DOMAIN", false);
    }

    @Bean
    public ExchangeWebSocketClient exchangeWebSocketClient() {
        return new ExchangeWebSocketClient() {
            @Override
            public Flux<StreamMarketDataEvent> streamTrades(String symbol) {
                return Flux.empty();
            }

            @Override
            public Flux<StreamMarketDataEvent> streamBookTicker(String symbol) {
                return Flux.empty();
            }
        };
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public TradingConfig tradingConfig() {
        return new TradingConfig(
            "BTCUSDT", 0.001, 1, 1.0, 14, 30.0, 70.0, 12, 26, 9, 20, 2.0, 900
        );
    }

    @Bean
    public FuturesTradingBot tradingBot() {
        FuturesTradingBot mockBot = Mockito.mock(FuturesTradingBot.class);
        Mockito.when(mockBot.getExchangeService()).thenReturn(new PaperFuturesExchangeService());
        Mockito.when(mockBot.getIndicatorCalculator()).thenReturn(Mockito.mock(IndicatorCalculator.class));
        Mockito.when(mockBot.getTrailingStopTracker()).thenReturn(Mockito.mock(TrailingStopTracker.class));
        Mockito.when(mockBot.getSentimentAnalyzer()).thenReturn(Mockito.mock(SentimentAnalyzer.class));
        PositionExitCondition mockExit = Mockito.mock(PositionExitCondition.class);
        Mockito.when(mockExit.shouldExit()).thenReturn(false);
        Mockito.when(mockBot.getExitConditions()).thenReturn(List.of(mockExit));
        Mockito.when(mockBot.getConfig()).thenReturn(tradingConfig());
        return mockBot;
    }

    @Bean
    public FuturesExchangeService exchangeService() {
        return Mockito.mock(FuturesExchangeService.class);
    }

    @Bean
    public SentimentAnalyzer sentimentAnalyzer() {
        return Mockito.mock(SentimentAnalyzer.class);
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public ProxyManager<String> authRateLimitProxyManager() {
        return Mockito.mock(ProxyManager.class);
    }

    @Bean
    public BucketConfiguration authRateLimitBucketConfiguration() {
        return Mockito.mock(BucketConfiguration.class);
    }

    @Bean
    public LLMProvider llmProvider() {
        return Mockito.mock(LLMProvider.class);
    }

    @Bean
    public UserRepository userRepository() {
        return Mockito.mock(UserRepository.class);
    }

    @Bean
    public RefreshTokenRepository refreshTokenRepository() {
        return Mockito.mock(RefreshTokenRepository.class);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, IndicatorValues> redisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }

    @Bean
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, BotState> botStateRedisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }

    @Bean
    public RateLimiter binanceTradingRateLimiter() {
        return Mockito.mock(RateLimiter.class);
    }

    @Bean
    public RateLimiter binanceMarketRateLimiter() {
        return Mockito.mock(RateLimiter.class);
    }

    @Bean
    public RateLimiter binanceAccountRateLimiter() {
        return Mockito.mock(RateLimiter.class);
    }

    @Bean
    public CircuitBreaker binanceApiCircuitBreaker() {
        return Mockito.mock(CircuitBreaker.class);
    }

    @Bean
    public Retry binanceApiRetry() {
        return Mockito.mock(Retry.class);
    }

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return Mockito.mock(ChatLanguageModel.class);
    }

    @Bean
    public ChatMemory chatMemory() {
        return Mockito.mock(ChatMemory.class);
    }

    @Bean
    public ExchangeServiceRegistry exchangeServiceRegistry() {
        return Mockito.mock(ExchangeServiceRegistry.class);
    }

    @Bean
    public AgentExecutionContext agentExecutionContext() {
        return Mockito.mock(AgentExecutionContext.class);
    }

    @Bean
    public OrderExecutionGatewayRegistry orderExecutionGatewayRegistry() {
        return Mockito.mock(OrderExecutionGatewayRegistry.class);
    }

    @Bean
    public TradingTools tradingTools() {
        return Mockito.mock(TradingTools.class);
    }

    @Bean
    public RAGService ragService() {
        return Mockito.mock(RAGService.class);
    }

    @Bean
    public TradeReflectionService tradeReflectionService() {
        return Mockito.mock(TradeReflectionService.class);
    }

    @Bean
    public TradingAgentService tradingAgentService() {
        return Mockito.mock(TradingAgentService.class);
    }

    @Bean
    public OrderPlacementService orderPlacementService() {
        return Mockito.mock(OrderPlacementService.class);
    }

    @Bean
    public TradingAgentFactory tradingAgentFactory() {
        return Mockito.mock(TradingAgentFactory.class);
    }

}
