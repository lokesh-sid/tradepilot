package tradingbot.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;

import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.messaging.EventPublisher;

@ExtendWith(MockitoExtension.class)
class BinanceFuturesServiceTest {

    @Mock
    private EventPublisher eventPublisher;

    private UMFuturesClientImpl mockClient;

    private BinanceFuturesService binanceFuturesService;

    @BeforeEach
    void setUp() {
        // Create mock with deep stubs to handle chaining calls like client.account().newOrder()
        mockClient = mock(UMFuturesClientImpl.class, Answers.RETURNS_DEEP_STUBS);
        
        binanceFuturesService = new BinanceFuturesService("apiKey", "apiSecret", eventPublisher);
        
        // Inject the mocked Binance client into the correct field "futuresClient"
        ReflectionTestUtils.setField(binanceFuturesService, "futuresClient", mockClient);
    }

    @Test
    void enterLongPosition_ShouldPublishEvent_WhenOrderSucceeds() {
        // Arrange
        String symbol = "BTCUSDT";
        double quantity = 0.01;
        String mockResponse = "{\"orderId\":12345,\"status\":\"FILLED\",\"executedQty\":\"0.01\",\"avgPrice\":\"50000.00\"}";
        // Note: getCurrentPrice calls client.market().markPrice()
        String mockMarkPriceResponse = "{\"symbol\":\"BTCUSDT\",\"markPrice\":\"50000.00\"}";

        // Mock client.account().newOrder(...)
        when(mockClient.account().newOrder(any(LinkedHashMap.class))).thenReturn(mockResponse);
        
        // Mock client.market().markPrice(...)
        when(mockClient.market().markPrice(any(LinkedHashMap.class))).thenReturn(mockMarkPriceResponse);

        // Act
        OrderResult result = binanceFuturesService.enterLongPosition(symbol, quantity);

        // Assert
        assertNotNull(result);
        assertEquals("12345", result.getExchangeOrderId());
        assertEquals(OrderResult.OrderStatus.FILLED, result.getStatus());

        verify(eventPublisher, times(1)).publishTradeExecution(any(TradeExecutionEvent.class));
    }

    @Test
    void enterShortPosition_ShouldPublishEvent_WhenOrderSucceeds() {
        // Arrange
        String symbol = "ETHUSDT";
        double quantity = 1.0;
        String mockResponse = "{\"orderId\":67890,\"status\":\"FILLED\",\"executedQty\":\"1.0\",\"avgPrice\":\"3000.00\"}";
        String mockMarkPriceResponse = "{\"symbol\":\"ETHUSDT\",\"markPrice\":\"3000.00\"}";

        when(mockClient.account().newOrder(any(LinkedHashMap.class))).thenReturn(mockResponse);
        when(mockClient.market().markPrice(any(LinkedHashMap.class))).thenReturn(mockMarkPriceResponse);

        // Act
        OrderResult result = binanceFuturesService.enterShortPosition(symbol, quantity);

        // Assert
        assertEquals("67890", result.getExchangeOrderId());
        verify(eventPublisher, times(1)).publishTradeExecution(any(TradeExecutionEvent.class));
    }
}
