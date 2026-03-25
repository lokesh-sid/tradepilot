package tradingbot.bot.controller.dto.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import tradingbot.bot.TradeDirection;
import tradingbot.bot.controller.dto.request.BotStateUpdateRequest.BotStatus;

@Schema(description = "Bot state update response")
public class BotStateResponse {
    
    @Schema(description = "Bot identifier", example = "bot-123")
    @JsonProperty("botId")
    private String botId;
    
    @Schema(description = "Current bot status", example = "RUNNING")
    @JsonProperty("status")
    private BotStatus status;
    
    @Schema(description = "Trading direction", example = "LONG")
    @JsonProperty("direction")
    private TradeDirection direction;
    
    @Schema(description = "Paper trading mode", example = "false")
    @JsonProperty("paperMode")
    private Boolean paperMode;
    
    @Schema(description = "State change timestamp", example = "2024-11-07T12:00:00Z")
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @Schema(description = "Transition result message", example = "Bot started successfully")
    @JsonProperty("message")
    private String message;
    
    @Schema(description = "Previous status", example = "STOPPED")
    @JsonProperty("previousStatus")
    private BotStatus previousStatus;
    
    @Schema(description = "Trading symbol", example = "BTCUSDT")
    @JsonProperty("symbol")
    private String symbol;
    
    @Schema(description = "Current position status", example = "OPEN")
    @JsonProperty("positionStatus")
    private String positionStatus;
    
    @Schema(description = "Entry price", example = "50000.0")
    @JsonProperty("entryPrice")
    private Double entryPrice;
    
    public BotStateResponse() {}
    
    // Getters and Setters
    public String getBotId() {
        return botId;
    }
    
    public void setBotId(String botId) {
        this.botId = botId;
    }
    
    public BotStatus getStatus() {
        return status;
    }
    
    public void setStatus(BotStatus status) {
        this.status = status;
    }
    
    public TradeDirection getDirection() {
        return direction;
    }
    
    public void setDirection(TradeDirection direction) {
        this.direction = direction;
    }
    
    public Boolean getPaperMode() {
        return paperMode;
    }
    
    public void setPaperMode(Boolean paperMode) {
        this.paperMode = paperMode;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public BotStatus getPreviousStatus() {
        return previousStatus;
    }
    
    public void setPreviousStatus(BotStatus previousStatus) {
        this.previousStatus = previousStatus;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public String getPositionStatus() {
        return positionStatus;
    }
    
    public void setPositionStatus(String positionStatus) {
        this.positionStatus = positionStatus;
    }
    
    public Double getEntryPrice() {
        return entryPrice;
    }
    
    public void setEntryPrice(Double entryPrice) {
        this.entryPrice = entryPrice;
    }
}
