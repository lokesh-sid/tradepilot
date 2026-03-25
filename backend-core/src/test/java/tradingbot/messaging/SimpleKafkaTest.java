package tradingbot.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import tradingbot.bot.TradeDirection;
import tradingbot.bot.events.TradeSignalEvent;
import tradingbot.bot.messaging.EventPublisher;

/**
 * Simple test for EventPublisher without requiring actual Kafka infrastructure
 */
@ExtendWith(MockitoExtension.class)
class SimpleKafkaTest {

    @Mock
    private KafkaTemplate<String, Object> mockKafkaTemplate;
    
    private EventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new EventPublisher(mockKafkaTemplate);
        // Enable Kafka publishing (normally injected by @Value — not set in pure unit tests)
        ReflectionTestUtils.setField(eventPublisher, "kafkaPublishEnabled", true);
    }

    @Test
    void shouldCreateEventPublisher() {
        assertThat(eventPublisher).isNotNull();
    }

    @Test
    void shouldPublishTradeSignalWithoutError() {
        // Given
        CompletableFuture<SendResult<String, Object>> successFuture = CompletableFuture.completedFuture(null);
        when(mockKafkaTemplate.send(anyString(), anyString(), any())).thenReturn(successFuture);
        
        TradeSignalEvent event = new TradeSignalEvent("bot-1", "BTCUSDT", TradeDirection.LONG);

        // When & Then - Should not throw exception
        CompletableFuture<Void> result = eventPublisher.publishTradeSignal(event);
        
        assertThat(result)
            .isNotNull()
            .succeedsWithin(java.time.Duration.ofSeconds(5));
    }
}