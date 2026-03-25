package tradingbot.bot.controller.dto.response;

/**
 * Response for resilience health check API
 */
public class ResilienceHealthResponse {
    private CircuitBreakerHealth circuitBreaker;
    private RateLimiterHealth rateLimiters;
    private OverallHealth overall;

    public ResilienceHealthResponse() {}

    public ResilienceHealthResponse(CircuitBreakerHealth circuitBreaker,
                                   RateLimiterHealth rateLimiters,
                                   OverallHealth overall) {
        this.circuitBreaker = circuitBreaker;
        this.rateLimiters = rateLimiters;
        this.overall = overall;
    }

    public CircuitBreakerHealth getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreakerHealth circuitBreaker) { this.circuitBreaker = circuitBreaker; }

    public RateLimiterHealth getRateLimiters() { return rateLimiters; }
    public void setRateLimiters(RateLimiterHealth rateLimiters) { this.rateLimiters = rateLimiters; }

    public OverallHealth getOverall() { return overall; }
    public void setOverall(OverallHealth overall) { this.overall = overall; }

    public static class CircuitBreakerHealth {
        private boolean healthy;
        private String state;

        public CircuitBreakerHealth() {}

        public CircuitBreakerHealth(boolean healthy, String state) {
            this.healthy = healthy;
            this.state = state;
        }

        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
    }

    public static class RateLimiterHealth {
        private boolean healthy;

        public RateLimiterHealth() {}

        public RateLimiterHealth(boolean healthy) {
            this.healthy = healthy;
        }

        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
    }

    public static class OverallHealth {
        private boolean healthy;

        public OverallHealth() {}

        public OverallHealth(boolean healthy) {
            this.healthy = healthy;
        }

        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
    }
}
