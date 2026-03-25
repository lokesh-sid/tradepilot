package tradingbot.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.messaging.EventPublisher;

@ExtendWith(MockitoExtension.class)
class DydxFuturesServiceTest {

    @Mock
    private EventPublisher eventPublisher;

    private DydxFuturesService dydxFuturesService;

    @BeforeEach
    void setUp() {
        dydxFuturesService = new DydxFuturesService(
                "testnet", 
                "https://mainnet.com", 
                "https://testnet.com", 
                "mock-private-key", 
                eventPublisher
        );
    }

    @Test
    void enterLongPosition_ShouldPublishMockEvent() {
        // Act
        OrderResult result = dydxFuturesService.enterLongPosition("BTC-USD", 1.0);

        // Assert
        assertNotNull(result);
        assertEquals(OrderResult.OrderStatus.FILLED, result.getStatus());
        
        // Verify that even though it's a mock order, it publishes an event
        verify(eventPublisher, times(1)).publishTradeExecution(any(TradeExecutionEvent.class));
    }

    @Test
    void enterShortPosition_ShouldPublishMockEvent() {
        // Act
        OrderResult result = dydxFuturesService.enterShortPosition("ETH-USD", 2.0);

        // Assert
        assertNotNull(result);
        verify(eventPublisher, times(1)).publishTradeExecution(any(TradeExecutionEvent.class));
    }
}
