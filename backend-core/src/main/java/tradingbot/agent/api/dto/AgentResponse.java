package tradingbot.agent.api.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AgentResponse - Response DTO for agent information
 * 
 * Provides comprehensive information about an AI trading agent including
 * its configuration, status, and recent decision-making data.
 */
@Schema(description = "AI trading agent information and status")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentResponse(
    @Schema(description = "Unique agent identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    String id,
    
    @Schema(description = "Agent name", example = "Bitcoin Trend Follower")
    String name,
    
    @Schema(description = "Agent goal type", example = "PROFIT_MAXIMIZATION")
    String goalType,
    
    @Schema(description = "Detailed goal description", example = "Maximize profit through trend-following strategy")
    String goalDescription,
    
    @Schema(description = "Trading symbol", example = "BTCUSDT")
    String tradingSymbol,
    
    @Schema(description = "Available capital", example = "1000.0")
    double capital,
    
    @Schema(description = "Current agent status", example = "ACTIVE")
    String status,
    
    @Schema(description = "Agent creation timestamp")
    Instant createdAt,
    
    @Schema(description = "Last activity timestamp")
    Instant lastActiveAt,
    
    @Schema(description = "Number of decision-making iterations", example = "42")
    int iterationCount,
    
    @Schema(description = "Latest market perception data")
    PerceptionDTO lastPerception,
    
    @Schema(description = "Latest reasoning and decision data")
    ReasoningDTO lastReasoning,
    
    @Schema(description = "Response timestamp in milliseconds", example = "1700000000000")
    long timestamp,
    
    @Schema(description = "Unique request identifier", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    String requestId,
    
    @Schema(description = "Exchange this agent trades on", example = "bybit")
    String exchangeName,

    @Schema(description = "Operation success indicator", example = "true")
    boolean success
) {
    
    @Schema(description = "Market perception data captured by the agent")
    public record PerceptionDTO(
        @Schema(description = "Trading symbol", example = "BTCUSDT")
        String symbol,
        
        @Schema(description = "Current market price", example = "45000.50")
        double currentPrice,
        
        @Schema(description = "Market trend direction", example = "UPTREND")
        String trend,
        
        @Schema(description = "Market sentiment", example = "BULLISH")
        String sentiment,
        
        @Schema(description = "Trading volume", example = "125000000.0")
        double volume,
        
        @Schema(description = "Perception timestamp")
        Instant timestamp
    ) {}
    
    @Schema(description = "Agent's reasoning and decision-making process")
    public record ReasoningDTO(
        @Schema(description = "Market observation", example = "Price breaking resistance at 45000")
        String observation,
        
        @Schema(description = "Detailed analysis", example = "Strong bullish momentum with increasing volume")
        String analysis,
        
        @Schema(description = "Risk assessment", example = "Moderate risk due to overbought RSI")
        String riskAssessment,
        
        @Schema(description = "Trading recommendation", example = "BUY with 2% position size")
        String recommendation,
        
        @Schema(description = "Confidence level (0-100)", example = "85")
        int confidence,
        
        @Schema(description = "Reasoning timestamp")
        Instant timestamp
    ) {}
}
