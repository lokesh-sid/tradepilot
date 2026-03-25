package tradingbot.bot.controller.dto.response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Error response following RFC 7807 Problem Details")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse extends BaseResponse {
    
    @Schema(description = "Error type URI", example = "https://api.tradingbot.com/errors/bot-not-found")
    @JsonProperty("type")
    private String type;
    
    @Schema(description = "Human-readable error title", example = "Bot Not Found")
    @JsonProperty("title")
    private String title;
    
    @Schema(description = "HTTP status code", example = "404")
    @JsonProperty("status")
    private Integer httpStatus;
    
    @Schema(description = "Detailed error message", example = "Bot with ID 'bot-123' does not exist")
    @JsonProperty("detail")
    private String detail;
    
    @Schema(description = "Request URI", example = "/api/v1/bots/bot-123")
    @JsonProperty("instance")
    private String instance;
    
    @Schema(description = "Field-level validation errors")
    @JsonProperty("fieldErrors")
    private Map<String, List<String>> fieldErrors;
    
    @Schema(description = "Trace ID for debugging", example = "abc123xyz")
    @JsonProperty("traceId")
    private String traceId;
    
    public ErrorResponse() {
        super();
    }
    
    // Backward compatibility constructors
    public ErrorResponse(String errorCode, String message) {
        this.type = ERROR_TYPE_BASE + errorCode.toLowerCase().replace("_", "-");
        this.title = errorCode.replace("_", " ");
        this.detail = message;
        this.httpStatus = 500; // Default to 500
    }
    
    public ErrorResponse(String errorCode, String message, String details) {
        this(errorCode, message);
        this.detail = details;
    }
    
    public ErrorResponse(String type, String title, Integer httpStatus, String detail) {
        this.type = type;
        this.title = title;
        this.httpStatus = httpStatus;
        this.detail = detail;
    }
    
    private static final String ERROR_TYPE_BASE = "https://api.tradingbot.com/errors/";
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final ErrorResponse response = new ErrorResponse();
        
        public Builder type(String type) {
            response.type = type;
            return this;
        }
        
        public Builder title(String title) {
            response.title = title;
            return this;
        }
        
        public Builder httpStatus(Integer status) {
            response.httpStatus = status;
            return this;
        }
        
        public Builder detail(String detail) {
            response.detail = detail;
            return this;
        }
        
        public Builder instance(String instance) {
            response.instance = instance;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            response.setTimestamp(timestamp);
            return this;
        }
        
        public Builder fieldErrors(Map<String, List<String>> fieldErrors) {
            response.fieldErrors = fieldErrors;
            return this;
        }
        
        public Builder addFieldError(String field, String error) {
            if (response.fieldErrors == null) {
                response.fieldErrors = new HashMap<>();
            }
            response.fieldErrors.computeIfAbsent(field, k -> new java.util.ArrayList<>()).add(error);
            return this;
        }
        
        public Builder traceId(String traceId) {
            response.traceId = traceId;
            return this;
        }
        
        public ErrorResponse build() {
            return response;
        }
    }
    
    // Getters and Setters
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public Integer getHttpStatus() {
        return httpStatus;
    }
    
    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }
    
    public String getDetail() {
        return detail;
    }
    
    public void setDetail(String detail) {
        this.detail = detail;
    }
    
    public String getInstance() {
        return instance;
    }
    
    public void setInstance(String instance) {
        this.instance = instance;
    }
    
    public Map<String, List<String>> getFieldErrors() {
        return fieldErrors;
    }
    
    public void setFieldErrors(Map<String, List<String>> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}