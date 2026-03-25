package tradingbot.bot.controller.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import tradingbot.bot.controller.dto.response.BotStartResponse;
import tradingbot.bot.controller.dto.response.BotStatusResponse;

class StartBotResponseTest {

    @Test
    void testBotStartResponseCreation() {
        // Given
        BotStatusResponse botStatus = new BotStatusResponse();
        botStatus.setRunning(true);
        botStatus.setSymbol("BTCUSDT");
        botStatus.setLeverage(10);
        
        // When
        BotStartResponse response = new BotStartResponse(
            "Trading bot started in LONG mode (live)",
            botStatus,
            "live",
            "LONG"
        );
        
        // Then
        assertEquals("Trading bot started in LONG mode (live)", response.getMessage());
        assertEquals("live", response.getMode());
        assertEquals("LONG", response.getDirection());
        assertNotNull(response.getBotStatus());
        assertEquals("BTCUSDT", response.getBotStatus().getSymbol());
        assertTrue(response.getBotStatus().isRunning());
        assertTrue(response.getTimestamp() > 0);
    }

    @Test
    void testDefaultConstructor() {
        // When
        BotStartResponse response = new BotStartResponse();
        
        // Then
        assertTrue(response.getTimestamp() > 0);
    }

    @Test
    void testSetters() {
        // Given
        BotStartResponse response = new BotStartResponse();
        BotStatusResponse botStatus = new BotStatusResponse();
        botStatus.setSymbol("ETHUSDT");
        
        // When
        response.setMessage("Test message");
        response.setMode("paper");
        response.setDirection("SHORT");
        response.setBotStatus(botStatus);
        response.setTimestamp(123456789L);
        
        // Then
        assertEquals("Test message", response.getMessage());
        assertEquals("paper", response.getMode());
        assertEquals("SHORT", response.getDirection());
        assertEquals("ETHUSDT", response.getBotStatus().getSymbol());
        assertEquals(123456789L, response.getTimestamp());
    }
}