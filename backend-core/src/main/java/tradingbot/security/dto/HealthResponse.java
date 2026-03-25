package tradingbot.security.dto;

/**
 * Response DTO for health check endpoint
 * 
 * Indicates the health status of the authentication service.
 */
public record HealthResponse(
        String status,
        String service
) {
    /**
     * Healthy status response
     */
    public static HealthResponse up() {
        return new HealthResponse("UP", "authentication");
    }
    
    /**
     * Unhealthy status response
     */
    public static HealthResponse down() {
        return new HealthResponse("DOWN", "authentication");
    }
}
