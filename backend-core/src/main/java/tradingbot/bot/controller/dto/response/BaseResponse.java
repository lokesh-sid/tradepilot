package tradingbot.bot.controller.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Base class for all API responses
 * 
 * Provides consistent metadata across all API responses:
 * - timestamp: Response generation time in milliseconds since epoch
 * - requestId: Unique identifier for request tracking and debugging
 * - success: Operation success indicator
 */
@Schema(description = "Base response class for all API operations")
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseResponse {

    @Schema(description = "Response timestamp in milliseconds since epoch", 
            example = "1700000000000",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("timestamp")
    private long timestamp;

    @Schema(description = "Unique request identifier for tracking and debugging", 
            example = "550e8400-e29b-41d4-a716-446655440000")
    @JsonProperty("requestId")
    private String requestId;

    @Schema(description = "Indicates if the operation was successful", 
            example = "true",
            defaultValue = "true")
    @JsonProperty("success")
    private boolean success;

    protected BaseResponse() {
        this.timestamp = System.currentTimeMillis();
        this.requestId = UUID.randomUUID().toString();
        this.success = true;
    }

    protected BaseResponse(boolean success) {
        this.timestamp = System.currentTimeMillis();
        this.requestId = UUID.randomUUID().toString();
        this.success = success;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}