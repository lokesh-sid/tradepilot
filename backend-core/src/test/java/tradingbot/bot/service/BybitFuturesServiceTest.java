package tradingbot.bot.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import tradingbot.bot.events.TradeExecutionEvent;
import tradingbot.bot.messaging.EventPublisher;

@ExtendWith(MockitoExtension.class)
class BybitFuturesServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private EventPublisher eventPublisher;

    private BybitFuturesService bybitFuturesService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        bybitFuturesService = new BybitFuturesService("testApiKey", "testApiSecret", "https://api-testnet.bybit.com", eventPublisher);
        // Inject the mocked RestTemplate
        ReflectionTestUtils.setField(bybitFuturesService, "restTemplate", restTemplate);
    }

    @Test
    void enterLongPosition_ShouldPublishEvent_WhenOrderSucceeds() throws Exception {
        // Arrange
        String symbol = "BTCUSDT";
        double quantity = 0.01;
        String mockOrderId = "123456789";
        
        // Mock successful order response from Bybit
        String jsonResponse = "{\"retCode\":0,\"retMsg\":\"OK\",\"result\":{\"orderId\":\"" + mockOrderId + "\",\"orderLinkId\":\"test-link-id\"}}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        // Mock getting current price (needed for the event)
        String priceResponse = "{\"retCode\":0,\"result\":{\"list\":[{\"lastPrice\":\"50000.00\"}]}}";
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(priceResponse, HttpStatus.OK));

        // Act
        OrderResult result = bybitFuturesService.enterLongPosition(symbol, quantity);

        // Assert
        assertNotNull(result);
        assertEquals(mockOrderId, result.getExchangeOrderId());
        assertEquals(OrderResult.OrderStatus.FILLED, result.getStatus());

        // Verify EventPublisher was called
        verify(eventPublisher).publishTradeExecution(any(TradeExecutionEvent.class));
    }
    
    @Test
    void enterShortPosition_ShouldPublishEvent_WhenOrderSucceeds() throws Exception {
        // Arrange
        String symbol = "ETHUSDT";
        double quantity = 0.1;
        String mockOrderId = "987654321";
        
        // Mock successful order response from Bybit
        String jsonResponse = "{\"retCode\":0,\"retMsg\":\"OK\",\"result\":{\"orderId\":\"" + mockOrderId + "\",\"orderLinkId\":\"test-link-id\"}}";
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(jsonResponse, HttpStatus.OK));

        // Mock getting current price
        String priceResponse = "{\"retCode\":0,\"result\":{\"list\":[{\"lastPrice\":\"3000.00\"}]}}";
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(priceResponse, HttpStatus.OK));

        // Act
        OrderResult result = bybitFuturesService.enterShortPosition(symbol, quantity);

        // Assert
        assertEquals(mockOrderId, result.getExchangeOrderId());
        verify(eventPublisher).publishTradeExecution(any(TradeExecutionEvent.class));
    }
}
