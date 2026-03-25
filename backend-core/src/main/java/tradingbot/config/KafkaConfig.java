package tradingbot.config;

import static org.apache.kafka.clients.consumer.ConsumerConfig.*;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka configuration for the trading bot event publishing system.
 * 
 * This configuration sets up Kafka producers for publishing trading events
 * with proper serialization for JSON payloads.
 */
@Configuration
public class KafkaConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.client-id:trading-bot-producer}")
    private String clientId;

    @Value("${spring.kafka.producer.acks:all}")
    private String acks;

    @Value("${spring.kafka.producer.retries:3}")
    private Integer retries;

    @Value("${spring.kafka.producer.batch-size:16384}")
    private Integer batchSize;

    @Value("${spring.kafka.producer.linger-ms:5}")
    private Integer lingerMs;

    @Value("${spring.kafka.producer.buffer-memory:33554432}")
    private Long bufferMemory;

    @Value("${spring.kafka.consumer.group-id:trading-bot}")
    private String groupId;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    /**
     * Kafka producer factory configuration.
     * 
     * @return ProducerFactory for creating Kafka producers
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Basic Kafka configuration
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        
        // Serialization configuration
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Reliability configuration
        configProps.put(ProducerConfig.ACKS_CONFIG, acks);
        configProps.put(ProducerConfig.RETRIES_CONFIG, retries);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Performance configuration
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        
        // Timeout configuration
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka template for publishing messages.
     * 
     * @return KafkaTemplate configured with the producer factory
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
    
    /**
     * Kafka consumer factory configuration.
     * 
     * @return ConsumerFactory for creating Kafka consumers
     */
    @Bean
    public org.springframework.kafka.core.ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Basic Kafka configuration
        configProps.put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(GROUP_ID_CONFIG, groupId);
        configProps.put(AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        
        // Serialization configuration
        configProps.put(KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class);
        configProps.put(VALUE_DESERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonDeserializer.class);
        
        // JSON deserializer configuration
        configProps.put(org.springframework.kafka.support.serializer.JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(org.springframework.kafka.support.serializer.JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");
        
        // Consumer configuration
        // IMPORTANT: manual ack mode is set on the container factory below;
        // disabling auto-commit here prevents offset advancement before the
        // listener has finished processing (eliminates silent data loss on crash).
        configProps.put(ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(SESSION_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(MAX_POLL_RECORDS_CONFIG, 500);
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }
    
    /**
     * Kafka listener container factory configuration.
     * 
     * @return ConcurrentKafkaListenerContainerFactory for @KafkaListener annotations
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            KafkaTemplate<String, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());

        // Configure concurrency (number of consumer threads per topic)
        factory.setConcurrency(3);

        // Manual acknowledgement — offset only advances after successful processing.
        // Combined with ENABLE_AUTO_COMMIT_CONFIG=false this prevents data loss on crash.
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);

        // Dead Letter Topic recoverer.
        // Default routing: {topic}.DLT on the same partition so ordering is preserved.
        // e.g. kline-closed.BTCUSDT  →  kline-closed.BTCUSDT.DLT
        // Override routing to add .DLT suffix explicitly (matches default, but is explicit).
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    logger.error(
                            "[DLQ] Routing failed record to DLT. topic={} partition={} offset={} " +
                            "key={} cause={}: {}",
                            record.topic(), record.partition(), record.offset(),
                            record.key(),
                            ex.getClass().getSimpleName(), ex.getMessage());
                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                });

        // Retry 3 times with 1-second fixed backoff before routing to DLT.
        // Non-retryable exceptions (e.g. deserialization failures, illegal state)
        // bypass retries and are sent directly to the DLT.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));

        // Exceptions that should NOT be retried — send straight to DLT.
        errorHandler.addNotRetryableExceptions(
                org.apache.kafka.common.errors.SerializationException.class,
                org.springframework.kafka.support.serializer.DeserializationException.class,
                IllegalArgumentException.class,
                IllegalStateException.class,
                NullPointerException.class);

        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}