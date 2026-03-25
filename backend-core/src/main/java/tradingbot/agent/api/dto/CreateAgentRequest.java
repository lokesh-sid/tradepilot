package tradingbot.agent.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tradingbot.bot.controller.validation.ValidSymbol;

/**
 * CreateAgentRequest - Request DTO for creating a new AI trading agent
 * 
 * Defines the configuration parameters for a new autonomous trading agent
 * including goals, capital allocation, and trading symbol.
 */
@Schema(description = "Request to create a new AI trading agent")
public record CreateAgentRequest(
    @Schema(description = "Agent name", 
            example = "Bitcoin Trend Follower",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Agent name is required")
    @Size(min = 3, max = 100, message = "Agent name must be between 3 and 100 characters")
    String name,
    
    @Schema(description = "Goal type (PROFIT_MAXIMIZATION, RISK_MINIMIZATION, BALANCED)", 
            example = "PROFIT_MAXIMIZATION",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Goal type is required")
    String goalType,
    
    @Schema(description = "Detailed goal description", 
            example = "Maximize profit through trend-following strategy on Bitcoin")
    @Size(max = 500, message = "Goal description must not exceed 500 characters")
    String goalDescription,
    
    @Schema(description = "Trading symbol (uppercase alphanumeric)", 
            example = "BTCUSDT",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Trading symbol is required")
    @ValidSymbol
    String tradingSymbol,
    
    @Schema(description = "Initial capital allocation",
            example = "1000.0",
            minimum = "0.01",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @DecimalMin(value = "0.01", message = "Capital must be at least 0.01")
    double capital,

    @Schema(description = "Exchange to trade on (binance, bybit, paper). Defaults to configured provider if omitted.",
            example = "bybit")
    String exchangeName
) {
}
