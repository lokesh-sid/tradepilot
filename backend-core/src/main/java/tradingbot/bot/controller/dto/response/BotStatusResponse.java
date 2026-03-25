package tradingbot.bot.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import tradingbot.bot.TradeDirection;

@Schema(description = "Trading bot status response")
public class BotStatusResponse extends BaseResponse {
    
    @Schema(description = "Bot running status", example = "true")
    @JsonProperty("running")
    private boolean running;
    
    @Schema(description = "Current trading direction", example = "LONG")
    @JsonProperty("direction")
    private TradeDirection direction;
    
    @Schema(description = "Trading symbol", example = "BTCUSDT")
    @JsonProperty("symbol")
    private String symbol;
    
    @Schema(description = "Current position status", example = "OPEN")
    @JsonProperty("positionStatus")
    private String positionStatus;
    
    @Schema(description = "Entry price", example = "50000.00")
    @JsonProperty("entryPrice")
    private Double entryPrice;
    
    @Schema(description = "Current leverage", example = "3")
    @JsonProperty("leverage")
    private Integer leverage;
    
    @Schema(description = "Paper trading mode", example = "false")
    @JsonProperty("paperMode")
    private boolean paperMode;
    
    @Schema(description = "Sentiment analysis enabled", example = "true")
    @JsonProperty("sentimentEnabled")
    private boolean sentimentEnabled;
    
    @Schema(description = "Detailed status message", example = "Bot Status: Running - LONG mode on BTCUSDT")
    @JsonProperty("statusMessage")
    private String statusMessage;
    
    public BotStatusResponse() {
        super();
    }
    
    // Getters and Setters
    public boolean isRunning() {
        return running;
    }
    
    public void setRunning(boolean running) {
        this.running = running;
    }
    
    public TradeDirection getDirection() {
        return direction;
    }
    
    public void setDirection(TradeDirection direction) {
        this.direction = direction;
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
    
    public Integer getLeverage() {
        return leverage;
    }
    
    public void setLeverage(Integer leverage) {
        this.leverage = leverage;
    }
    
    public boolean isPaperMode() {
        return paperMode;
    }
    
    public void setPaperMode(boolean paperMode) {
        this.paperMode = paperMode;
    }
    
    public boolean isSentimentEnabled() {
        return sentimentEnabled;
    }
    
    public void setSentimentEnabled(boolean sentimentEnabled) {
        this.sentimentEnabled = sentimentEnabled;
    }
    
    public String getStatusMessage() {
        return statusMessage;
    }
    
    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }
}