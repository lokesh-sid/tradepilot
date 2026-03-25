package tradingbot.config;

import java.util.List;

import org.mockito.Mockito;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
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
import tradingbot.bot.metrics.TradingMetrics;
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
import tradingbot.security.repository.UserRepository;

/**
 * Test configuration for Futures Trading Bot Integration Tests.
 * Provides minimal Spring context with in-memory implementations.
 */
@SpringBootConfiguration
@TestPropertySource("classpath:application-test.properties")
@EnableAutoConfiguration(exclude = {
    RedisAutoConfiguration.class,
    RedisRepositoriesAutoConfiguration.class,
    SecurityAutoConfiguration.class,
    ManagementWebSecurityAutoConfiguration.class
})
@ComponentScan(
    basePackages = {
        "tradingbot.bot.controller",
        "tradingbot.bot.service",
        "tradingbot.agent.application",
        "tradingbot.agent.impl",
        "tradingbot.agent.infrastructure.repository",
        "tradingbot.agent.api"
    },
    useDefaultFilters = true,
    excludeFilters = {
        @Filter(type = FilterType.ANNOTATION, classes = EnableAutoConfiguration.class)
    }
)
@Import({InstanceConfig.class, AgentManager.class, AgentFactory.class})
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
public class FuturesTradingBotIntegrationTestConfig {
    @Bean
    public tradingbot.agent.config.AgentProperties agentProperties() {
        return new tradingbot.agent.config.AgentProperties();
    }

    @Bean
    public TradingSafetyService tradingSafetyService() {
        return new TradingSafetyService("paper", "paper", "TESTNET_DOMAIN", false);
    }

    // Use Spring Boot's auto-configured DataSource (Testcontainers/Postgres) for integration tests
    // No explicit DataSource bean; rely on application-container-test.properties and @DynamicPropertySource

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
            "BTCUSDT",           // symbol
            0.001,              // tradeAmount
            1,                  // leverage
            1.0,                // trailingStopPercent
            14,                 // lookbackPeriodRsi
            30.0,               // rsiOversoldThreshold
            70.0,               // rsiOverboughtThreshold
            12,                 // macdFastPeriod
            26,                 // macdSlowPeriod
            9,                  // macdSignalPeriod
            20,                 // bbPeriod
            2.0,                // bbStandardDeviation
            900                 // interval
        );
    }

    @Bean
    public FuturesTradingBot tradingBot() {
        // Create a proper mock with required dependencies
        FuturesTradingBot mockBot = Mockito.mock(FuturesTradingBot.class);
        
        // Mock the required dependencies that createBot() method uses
        Mockito.when(mockBot.getExchangeService()).thenReturn(new PaperFuturesExchangeService());
        Mockito.when(mockBot.getIndicatorCalculator()).thenReturn(Mockito.mock(IndicatorCalculator.class));
        Mockito.when(mockBot.getTrailingStopTracker()).thenReturn(Mockito.mock(TrailingStopTracker.class));
        Mockito.when(mockBot.getSentimentAnalyzer()).thenReturn(Mockito.mock(SentimentAnalyzer.class));
        
        // Create a simple mock exit condition that never triggers
        PositionExitCondition mockExitCondition = Mockito.mock(PositionExitCondition.class);
        Mockito.when(mockExitCondition.shouldExit()).thenReturn(false);
        Mockito.when(mockBot.getExitConditions()).thenReturn(List.of(mockExitCondition)); // Non-empty list
        
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
    public LLMProvider llmProvider() {
        return Mockito.mock(LLMProvider.class);
    }

    @Bean
    public UserRepository userRepository() {
        return Mockito.mock(UserRepository.class);
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

    // Resilience4j beans for ResilienceController
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

    // LangChain4j beans for TradingAgentService
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


    @Bean
    public TradingMetrics tradingMetrics() {
        return Mockito.mock(TradingMetrics.class);
    }
}