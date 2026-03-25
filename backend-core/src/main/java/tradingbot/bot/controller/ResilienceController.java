package tradingbot.bot.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import tradingbot.bot.controller.dto.response.AllResilienceMetricsResponse;
import tradingbot.bot.controller.dto.response.CircuitBreakerMetricsResponse;
import tradingbot.bot.controller.dto.response.RateLimiterMetricsResponse;
import tradingbot.bot.controller.dto.response.ResilienceHealthResponse;
import tradingbot.bot.controller.dto.response.RetryMetricsResponse;

/**
 * REST controller for monitoring rate limiting and resilience metrics
 */
@RestController
@RequestMapping("/api/resilience")
@Tag(name = "Resilience Controller", description = "API for monitoring rate limiting, circuit breaker, and retry metrics")
public class ResilienceController {

    private final RateLimiter binanceTradingRateLimiter;
    private final RateLimiter binanceMarketRateLimiter;
    private final RateLimiter binanceAccountRateLimiter;
    private final CircuitBreaker binanceApiCircuitBreaker;
    private final Retry binanceApiRetry;

    public ResilienceController(RateLimiter binanceTradingRateLimiter,
                               RateLimiter binanceMarketRateLimiter,
                               RateLimiter binanceAccountRateLimiter,
                               CircuitBreaker binanceApiCircuitBreaker,
                               Retry binanceApiRetry) {
        this.binanceTradingRateLimiter = binanceTradingRateLimiter;
        this.binanceMarketRateLimiter = binanceMarketRateLimiter;
        this.binanceAccountRateLimiter = binanceAccountRateLimiter;
        this.binanceApiCircuitBreaker = binanceApiCircuitBreaker;
        this.binanceApiRetry = binanceApiRetry;
    }

    /**
     * Get current rate limiter metrics
     */
    @GetMapping("/rate-limiters")
    @Operation(summary = "Get rate limiter metrics", 
               description = "Returns current metrics for all rate limiters (trading, market, account)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rate limiter metrics retrieved successfully",
                    content = @Content(mediaType = "application/json", 
                                     schema = @Schema(implementation = RateLimiterMetricsResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public RateLimiterMetricsResponse getRateLimiterMetrics() {
        // Trading rate limiter metrics
        RateLimiterMetricsResponse.RateLimiterMetrics trading = new RateLimiterMetricsResponse.RateLimiterMetrics(
            binanceTradingRateLimiter.getMetrics().getAvailablePermissions(),
            binanceTradingRateLimiter.getMetrics().getNumberOfWaitingThreads()
        );
        
        // Market rate limiter metrics
        RateLimiterMetricsResponse.RateLimiterMetrics market = new RateLimiterMetricsResponse.RateLimiterMetrics(
            binanceMarketRateLimiter.getMetrics().getAvailablePermissions(),
            binanceMarketRateLimiter.getMetrics().getNumberOfWaitingThreads()
        );
        
        // Account rate limiter metrics
        RateLimiterMetricsResponse.RateLimiterMetrics account = new RateLimiterMetricsResponse.RateLimiterMetrics(
            binanceAccountRateLimiter.getMetrics().getAvailablePermissions(),
            binanceAccountRateLimiter.getMetrics().getNumberOfWaitingThreads()
        );
        
        return new RateLimiterMetricsResponse(trading, market, account);
    }

    /**
     * Get circuit breaker metrics
     */
    @GetMapping("/circuit-breaker")
    @Operation(summary = "Get circuit breaker metrics", 
               description = "Returns current circuit breaker state and metrics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Circuit breaker metrics retrieved successfully",
                    content = @Content(mediaType = "application/json", 
                                     schema = @Schema(implementation = CircuitBreakerMetricsResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public CircuitBreakerMetricsResponse getCircuitBreakerMetrics() {
        CircuitBreaker.Metrics cbMetrics = binanceApiCircuitBreaker.getMetrics();
        
        return new CircuitBreakerMetricsResponse(
            binanceApiCircuitBreaker.getState().toString(),
            cbMetrics.getFailureRate(),
            cbMetrics.getNumberOfBufferedCalls(),
            cbMetrics.getNumberOfFailedCalls(),
            cbMetrics.getNumberOfSuccessfulCalls(),
            cbMetrics.getNumberOfNotPermittedCalls()
        );
    }

    /**
     * Get retry metrics
     */
    @GetMapping("/retry")
    @Operation(summary = "Get retry metrics", 
               description = "Returns retry attempt statistics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Retry metrics retrieved successfully",
                    content = @Content(mediaType = "application/json", 
                                     schema = @Schema(implementation = RetryMetricsResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public RetryMetricsResponse getRetryMetrics() {
        Retry.Metrics retryMetrics = binanceApiRetry.getMetrics();
        
        return new RetryMetricsResponse(
            retryMetrics.getNumberOfSuccessfulCallsWithoutRetryAttempt(),
            retryMetrics.getNumberOfSuccessfulCallsWithRetryAttempt(),
            retryMetrics.getNumberOfFailedCallsWithRetryAttempt(),
            retryMetrics.getNumberOfFailedCallsWithoutRetryAttempt()
        );
    }

    /**
     * Get all resilience metrics in one call
     */
    @GetMapping("/metrics")
    @Operation(summary = "Get all resilience metrics", 
               description = "Returns combined rate limiter, circuit breaker, and retry metrics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "All resilience metrics retrieved successfully",
                    content = @Content(mediaType = "application/json", 
                                     schema = @Schema(implementation = AllResilienceMetricsResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public AllResilienceMetricsResponse getAllMetrics() {
        return new AllResilienceMetricsResponse(
            getRateLimiterMetrics(),
            getCircuitBreakerMetrics(),
            getRetryMetrics()
        );
    }

    /**
     * Health check endpoint for resilience components
     */
    @GetMapping("/health")
    @Operation(summary = "Get resilience health status", 
               description = "Returns health status of circuit breaker and rate limiters")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Health status retrieved successfully",
                    content = @Content(mediaType = "application/json", 
                                     schema = @Schema(implementation = ResilienceHealthResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResilienceHealthResponse getHealthStatus() {
        // Check if circuit breaker is operational
        boolean circuitBreakerHealthy = binanceApiCircuitBreaker.getState() != CircuitBreaker.State.OPEN;
        ResilienceHealthResponse.CircuitBreakerHealth circuitBreakerHealth = 
            new ResilienceHealthResponse.CircuitBreakerHealth(
                circuitBreakerHealthy,
                binanceApiCircuitBreaker.getState().toString()
            );
        
        // Check if rate limiters have available capacity
        boolean rateLimitersHealthy = 
            binanceTradingRateLimiter.getMetrics().getAvailablePermissions() > 0 ||
            binanceMarketRateLimiter.getMetrics().getAvailablePermissions() > 0 ||
            binanceAccountRateLimiter.getMetrics().getAvailablePermissions() > 0;
        
        ResilienceHealthResponse.RateLimiterHealth rateLimitersHealth = 
            new ResilienceHealthResponse.RateLimiterHealth(rateLimitersHealthy);
        
        // Overall health
        boolean overallHealthy = circuitBreakerHealthy && rateLimitersHealthy;
        ResilienceHealthResponse.OverallHealth overallHealth = 
            new ResilienceHealthResponse.OverallHealth(overallHealthy);
        
        return new ResilienceHealthResponse(circuitBreakerHealth, rateLimitersHealth, overallHealth);
    }
}
