package tradingbot.bot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import tradingbot.bot.FuturesTradingBot.BotParams;

/**
 * Test class to demonstrate Spring validation in the FuturesTradingBot.Builder
 */
class FuturesTradingBotValidationTest {

    @Test
    void testValidationWithMissingExchangeService() {
        // Test that validation properly catches missing exchange service
        BotParams.Builder builder = new BotParams.Builder();
        // Intentionally not setting exchangeService
        builder.tradeDirection(TradeDirection.LONG);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
        
        assertTrue(exception.getMessage().contains("Exchange service is required"));
    }

    @Test
    void testValidationWithMissingTradeDirection() {
        // Test that validation properly catches missing trade direction
        BotParams.Builder builder = new BotParams.Builder();
        // Intentionally not setting trade direction
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build);
        
        assertTrue(exception.getMessage().contains("Trade direction is required"));
    }

    @Test
    void testValidationWithEmptyExitConditions() {
        // Test that validation properly catches empty exit conditions
        // The setter method itself throws IllegalArgumentException for empty list
        BotParams.Builder builder = new BotParams.Builder();
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
            () -> {
                builder.exitConditions(Collections.emptyList());
            }
        ); 
        
        assertTrue(exception.getMessage().contains("Exit conditions cannot be empty"));
    }
    
    @Test
    void testValidationWithMultipleErrors() {
        // Test that validation catches multiple missing fields at once
        BotParams.Builder builder = new BotParams.Builder();
        // Not setting any required fields
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> {
                builder.build();
            }
        );
        
        String errorMessage = exception.getMessage();
        assertTrue(errorMessage.contains("Exchange service is required"));
        assertTrue(errorMessage.contains("Trade direction is required"));
        assertTrue(errorMessage.contains("Indicator calculator is required"));
        assertTrue(errorMessage.contains("Trailing stop tracker is required"));
        assertTrue(errorMessage.contains("Sentiment analyzer is required"));
        assertTrue(errorMessage.contains("Trading config is required"));
    }
}