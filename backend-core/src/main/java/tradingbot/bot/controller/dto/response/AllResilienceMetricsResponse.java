package tradingbot.bot.controller.dto.response;

/**
 * Response for all resilience metrics API
 */
public class AllResilienceMetricsResponse {
    private RateLimiterMetricsResponse rateLimiters;
    private CircuitBreakerMetricsResponse circuitBreaker;
    private RetryMetricsResponse retry;

    public AllResilienceMetricsResponse() {}

    public AllResilienceMetricsResponse(RateLimiterMetricsResponse rateLimiters,
                                       CircuitBreakerMetricsResponse circuitBreaker,
                                       RetryMetricsResponse retry) {
        this.rateLimiters = rateLimiters;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    public RateLimiterMetricsResponse getRateLimiters() { return rateLimiters; }
    public void setRateLimiters(RateLimiterMetricsResponse rateLimiters) { this.rateLimiters = rateLimiters; }

    public CircuitBreakerMetricsResponse getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreakerMetricsResponse circuitBreaker) { this.circuitBreaker = circuitBreaker; }

    public RetryMetricsResponse getRetry() { return retry; }
    public void setRetry(RetryMetricsResponse retry) { this.retry = retry; }
}
