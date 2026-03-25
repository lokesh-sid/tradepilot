package tradingbot.infrastructure.marketdata.binance;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import tradingbot.agent.config.OrderExecutionGatewayRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import reactor.core.publisher.Flux;
import tradingbot.agent.application.AgentOrchestrator;
import tradingbot.agent.application.PerformanceTrackingService;
import tradingbot.agent.application.strategy.LangChain4jStrategy;
import tradingbot.agent.domain.repository.AgentRepository;
import tradingbot.agent.infrastructure.repository.OrderRepository;
import tradingbot.bot.metrics.TradingMetrics;
import tradingbot.domain.market.KlineClosedEvent;
import tradingbot.domain.market.StreamMarketDataEvent;
import tradingbot.infrastructure.marketdata.ExchangeWebSocketClient;
/**
 * Integration test verifying the end-to-end Kafka plumbing for
 * {@link KlineClosedEvent}.
 *
 * <p>A {@link KafkaContainer} (Testcontainers) is started once per test class.
 * One synthetic {@code kline-closed.BTCUSDT} message is published to the
 * container, and the test asserts that
 * {@link AgentOrchestrator#onKlineClosedEvent} is invoked by the live
 * Kafka listener container.
 */
@Tag("requires-docker")
@Testcontainers
@SpringJUnitConfig(classes = KlineClosedEventKafkaIntegrationTest.TestConfig.class)
@org.springframework.test.context.TestPropertySource(properties = {
        // Provide a non-empty SpEL map so AgentOrchestrator's
        // @Value("#{${agent.throttle.per-symbol:{}}}") evaluates to Map<String,Long>
        // rather than the empty list that bare '{}' produces in SpEL.
        "agent.throttle.per-symbol={BTCUSDT: 5000}",
        "agent.throttle.default-ms=5000",
        "websocket.enabled=false",
        "agent.strategy=langchain4j"
})
@DisplayName("KlineClosedEvent Kafka Integration Test")
class KlineClosedEventKafkaIntegrationTest {

    @Container
    static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void kafkaBootstrapServers(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    /** Spy bean — wrapped by {@link TestConfig#agentOrchestrator}. */
    @Autowired
    AgentOrchestrator agentOrchestrator;

    // -------------------------------------------------------------------------
    // Test
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("AgentOrchestrator.onKlineClosedEvent is invoked when a kline-closed message is published")
    void onKlineClosedEvent_isInvokedWhenMessagePublished() {
        KlineClosedEvent event = new KlineClosedEvent(
                "BINANCE",
                "BTCUSDT",
                "1m",
                new BigDecimal("29000.00"),
                new BigDecimal("29500.00"),
                new BigDecimal("28900.00"),
                new BigDecimal("29400.00"),
                new BigDecimal("100.5"),
                Instant.now().minusSeconds(60),
                Instant.now());

        kafkaTemplate.send("kline-closed.BTCUSDT", "BTCUSDT", event);

        // Wait up to 10 s for the @KafkaListener to be invoked
        verify(agentOrchestrator, timeout(10_000)).onKlineClosedEvent(any(KlineClosedEvent.class));
    }

    // -------------------------------------------------------------------------
    // Test configuration
    // -------------------------------------------------------------------------

    @EnableKafka
    @TestConfiguration
    static class TestConfig {

        // --- Kafka infrastructure ------------------------------------------------

        @Bean
        ProducerFactory<String, Object> producerFactory(
                org.springframework.core.env.Environment env) {
            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                    env.getProperty("spring.kafka.bootstrap-servers"));
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                    org.apache.kafka.common.serialization.StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            return new DefaultKafkaProducerFactory<>(props);
        }

        @Bean
        KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
            return new KafkaTemplate<>(pf);
        }

        @Bean
        ConsumerFactory<String, Object> consumerFactory(
                org.springframework.core.env.Environment env) {
            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                    env.getProperty("spring.kafka.bootstrap-servers"));
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "agent-orchestrator-klines-test");
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                    org.apache.kafka.common.serialization.StringDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
            props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
            props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "true");
            return new DefaultKafkaConsumerFactory<>(props);
        }

        @Bean
        ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
                ConsumerFactory<String, Object> consumerFactory) {
            ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory);
            factory.getContainerProperties().setAckMode(
                    org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
            return factory;
        }

        // --- AgentOrchestrator and its mocked dependencies -----------------------

        @Bean
        AgentRepository agentRepository() {
            AgentRepository repo = mock(AgentRepository.class);
            Mockito.when(repo.findAllActive()).thenReturn(Collections.emptyList());
            return repo;
        }

        @Bean
        LangChain4jStrategy langChain4jStrategy() {
            return mock(LangChain4jStrategy.class);
        }

        @Bean
        ExchangeWebSocketClient exchangeWebSocketClient() {
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
        BulkheadRegistry bulkheadRegistry() {
            return BulkheadRegistry.ofDefaults();
        }

        @Bean
        OrderRepository orderRepository() {
            return mock(OrderRepository.class);
        }

        @Bean
        PerformanceTrackingService performanceTrackingService() {
            return mock(PerformanceTrackingService.class);
        }

        @Bean
        ApplicationEventPublisher applicationEventPublisher() {
            return mock(ApplicationEventPublisher.class);
        }

        @Bean
        TradingMetrics tradingMetrics() {
            return mock(TradingMetrics.class);
        }

        /**
         * The real {@link AgentOrchestrator} wrapped in a Mockito spy so that
         * the test can verify {@link AgentOrchestrator#onKlineClosedEvent} is
         * invoked, while still allowing the {@link org.springframework.kafka.annotation.KafkaListener}
         * annotation on the method to be picked up by Spring's post-processor.
         */
        @Bean
        OrderExecutionGatewayRegistry orderExecutionGatewayRegistry() {
            return Mockito.mock(OrderExecutionGatewayRegistry.class);
        }

        @Bean
        AgentOrchestrator agentOrchestrator(
                AgentRepository agentRepository,
                LangChain4jStrategy langChain4jStrategy,
                ExchangeWebSocketClient exchangeWebSocketClient,
                BulkheadRegistry bulkheadRegistry,
                OrderExecutionGatewayRegistry orderExecutionGatewayRegistry,
                OrderRepository orderRepository,
                PerformanceTrackingService performanceTrackingService,
                ApplicationEventPublisher applicationEventPublisher,
                TradingMetrics tradingMetrics) {

            List<tradingbot.agent.ReactiveTradingAgent> emptyAgents = Collections.emptyList();

            AgentOrchestrator real = new AgentOrchestrator(
                    agentRepository,
                    langChain4jStrategy,
                    exchangeWebSocketClient,
                    emptyAgents,
                    bulkheadRegistry,
                    null,
                    orderExecutionGatewayRegistry,
                    orderRepository,
                    performanceTrackingService,
                    applicationEventPublisher,
                    tradingMetrics,
                    "langchain4j");

            return Mockito.spy(real);
        }
    }
}
