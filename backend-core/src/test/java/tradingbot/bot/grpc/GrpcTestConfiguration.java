package tradingbot.bot.grpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import tradingbot.bot.controller.dto.BotState;
import tradingbot.bot.service.BotCacheService;
import tradingbot.agent.TradingAgent;
import tradingbot.agent.manager.AgentManager;

/**
 * Minimal test configuration for gRPC integration tests
 * Only loads what's needed for BotManagementService gRPC tests
 */
@TestConfiguration
@EnableAutoConfiguration(exclude = {
    HibernateJpaAutoConfiguration.class,
    DataSourceAutoConfiguration.class,
    KafkaAutoConfiguration.class
})
@Import({BotManagementServiceImpl.class, BotCacheService.class})
public class GrpcTestConfiguration {

    @Bean
    ObjectMapper grpcObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    AgentManager agentManager() {
        AgentManager manager = mock(AgentManager.class);
        AtomicLong nextId = new AtomicLong(1000);
        when(manager.createAgent(any())).thenAnswer(invocation -> {
            TradingAgent agent = mock(TradingAgent.class);
            when(agent.getId()).thenReturn(Long.toString(nextId.incrementAndGet()));
            return agent;
        });
        return manager;
    }
    
    @Bean
    RedisTemplate<String, BotState> botStateRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, BotState> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        ObjectMapper mapper = grpcObjectMapper();
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(mapper, BotState.class));
        return template;
    }
}
