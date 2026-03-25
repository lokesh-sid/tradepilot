package tradingbot.bot.grpc;

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
    RedisTemplate<String, BotState> botStateRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, BotState> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(mapper, BotState.class));
        return template;
    }
}
