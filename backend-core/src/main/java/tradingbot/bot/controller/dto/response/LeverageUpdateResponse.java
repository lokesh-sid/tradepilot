package tradingbot.bot.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response for leverage update operation")
public class LeverageUpdateResponse extends BaseResponse {
    
    @Schema(description = "Success message", example = "Leverage updated to 10x")
    @JsonProperty("message")
    private String message;
    
    @Schema(description = "Updated leverage value", example = "10.0")
    @JsonProperty("newLeverage")
    private Double newLeverage;
    
    @Schema(description = "Previous leverage value", example = "5.0")
    @JsonProperty("previousLeverage")
    private Double previousLeverage;
    
    public LeverageUpdateResponse() {
        super();
    }
    
    public LeverageUpdateResponse(String message, Double newLeverage, Double previousLeverage) {
        super();
        this.message = message;
        this.newLeverage = newLeverage;
        this.previousLeverage = previousLeverage;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Double getNewLeverage() {
        return newLeverage;
    }
    
    public void setNewLeverage(Double newLeverage) {
        this.newLeverage = newLeverage;
    }
    
    public Double getPreviousLeverage() {
        return previousLeverage;
    }
    
    public void setPreviousLeverage(Double previousLeverage) {
        this.previousLeverage = previousLeverage;
    }
}