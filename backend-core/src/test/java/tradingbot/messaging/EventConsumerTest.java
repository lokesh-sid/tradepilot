package tradingbot.messaging;

import static org.mockito.Mockito.*;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tradingbot.bot.TradeDirection;
import tradingbot.bot.events.BotStatusEvent;
import tradingbot.bot.events.MarketDataEvent;
import tradingbot.bot.events.RiskEvent;
import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.events.TradeSignalEvent;
import tradingbot.bot.messaging.EventConsumer;
import tradingbot.bot.messaging.EventPublisher.EventWrapper;
import tradingbot.bot.persistence.service.EventPersistenceService;

/**
 * Unit tests for the Kafka EventConsumer.
 *
 * Tests the message consumption and event persistence logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventConsumer Tests")
class EventConsumerTest {

    @Mock
    private EventPersistenceService eventPersistenceService;

    @InjectMocks
    private EventConsumer eventConsumer;

    @Test
    @DisplayName("Should persist trade signal event successfully")
    void shouldPersistTradeSignalEvent() {
        // Given
        TradeSignalEvent event = new TradeSignalEvent("bot-1", "BTCUSDT", TradeDirection.LONG);
        event.setEventId("trade-signal-123");
        event.setStrength(0.8);

        EventWrapper wrapper = new EventWrapper(
            "trade-signal-123", Instant.now(), "TRADE_SIGNAL", event, "bot-1");

        // When
        eventConsumer.handleTradeSignal(wrapper, 0, 100L, "bot-1");

        // Then
        verify(eventPersistenceService).persistEvent(event);
    }

    @Test
    @DisplayName("Should persist trade execution event successfully")
    void shouldPersistTradeExecutionEvent() {
        // Given
        TradeExecutionEvent event = new TradeExecutionEvent("bot-1", "order-123", "BTCUSDT");
        event.setEventId("trade-execution-456");
        event.setSide("BUY");
        event.setQuantity(1.5);
        event.setPrice(45000.0);

        EventWrapper wrapper = new EventWrapper(
            "trade-execution-456", Instant.now(), "TRADE_EXECUTED", event, "bot-1");

        // When
        eventConsumer.handleTradeExecution(wrapper, 0, 101L, "bot-1");

        // Then
        verify(eventPersistenceService).persistEvent(event);
    }

    @Test
    @DisplayName("Should persist risk event successfully")
    void shouldPersistRiskEvent() {
        // Given
        RiskEvent event = new RiskEvent("bot-1", "POSITION_SIZE_EXCEEDED", "BTCUSDT");
        event.setEventId("risk-event-789");
        event.setSeverity("HIGH");
        event.setDescription("Reduce position");

        EventWrapper wrapper = new EventWrapper(
            "risk-event-789", Instant.now(), "RISK_ALERT", event, "bot-1");

        // When
        eventConsumer.handleRiskEvent(wrapper, 0, 102L, "bot-1");

        // Then
        verify(eventPersistenceService).persistEvent(event);
    }

    @Test
    @DisplayName("Should persist market data event successfully")
    void shouldPersistMarketDataEvent() {
        // Given
        MarketDataEvent event = new MarketDataEvent("bot-1", "BTCUSDT", 45000.0);
        event.setEventId("market-data-321");
        event.setVolume(1000.0);

        EventWrapper wrapper = new EventWrapper(
            "market-data-321", Instant.now(), "MARKET_DATA", event, "BTCUSDT");

        // When
        eventConsumer.handleMarketData(wrapper, 0, 103L, "BTCUSDT");

        // Then
        verify(eventPersistenceService).persistEvent(event);
    }

    @Test
    @DisplayName("Should persist bot status event successfully")
    void shouldPersistBotStatusEvent() {
        // Given
        BotStatusEvent event = new BotStatusEvent("bot-1", "RUNNING");
        event.setEventId("bot-status-654");

        EventWrapper wrapper = new EventWrapper(
            "bot-status-654", Instant.now(), "BOT_STATUS", event, "bot-1");

        // When
        eventConsumer.handleBotStatus(wrapper, 0, 104L, "bot-1");

        // Then
        verify(eventPersistenceService).persistEvent(event);
    }
}