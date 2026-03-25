package tradingbot.bot.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tradingbot.bot.TradeDirection;

@Schema(description = "Request to update bot state")
public class BotStateUpdateRequest {
    
    @NotNull(message = "Status is required")
    @Schema(description = "Target bot status (RUNNING, STOPPED, PAUSED)", example = "RUNNING", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("status")
    private BotStatus status;
    
    @Schema(description = "Trading direction (required when starting)", example = "LONG")
    @JsonProperty("direction")
    private TradeDirection direction;
    
    @NotNull(message = "Paper mode flag is required")
    @Schema(description = "Enable paper trading", example = "true", defaultValue = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("paperMode")
    private Boolean paperMode = true;
    
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    @Schema(description = "Reason for state change", example = "User initiated stop")
    @JsonProperty("reason")
    private String reason;
    
    public BotStateUpdateRequest() {}
    
    public BotStateUpdateRequest(BotStatus status, TradeDirection direction, Boolean paperMode, String reason) {
        this.status = status;
        this.direction = direction;
        this.paperMode = paperMode != null ? paperMode : true;
        this.reason = reason;
    }
    
    // Cross-field validation
    @AssertTrue(message = "Direction is required when starting bot")
    private boolean isDirectionValid() {
        return status != BotStatus.RUNNING || direction != null;
    }
    
    // Getters and Setters
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
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public enum BotStatus {
        RUNNING,
        STOPPED,
        PAUSED
    }
}
