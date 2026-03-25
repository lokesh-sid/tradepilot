package tradingbot.bot.controller.dto.response;

/**
 * Response for circuit breaker metrics API
 */
public class CircuitBreakerMetricsResponse {
    private String state;
    private float failureRate;
    private int numberOfBufferedCalls;
    private long numberOfFailedCalls;
    private long numberOfSuccessfulCalls;
    private long numberOfNotPermittedCalls;

    public CircuitBreakerMetricsResponse() {}

    public CircuitBreakerMetricsResponse(String state, float failureRate, int numberOfBufferedCalls,
                                         long numberOfFailedCalls, long numberOfSuccessfulCalls,
                                         long numberOfNotPermittedCalls) {
        this.state = state;
        this.failureRate = failureRate;
        this.numberOfBufferedCalls = numberOfBufferedCalls;
        this.numberOfFailedCalls = numberOfFailedCalls;
        this.numberOfSuccessfulCalls = numberOfSuccessfulCalls;
        this.numberOfNotPermittedCalls = numberOfNotPermittedCalls;
    }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public float getFailureRate() { return failureRate; }
    public void setFailureRate(float failureRate) { this.failureRate = failureRate; }

    public int getNumberOfBufferedCalls() { return numberOfBufferedCalls; }
    public void setNumberOfBufferedCalls(int numberOfBufferedCalls) { this.numberOfBufferedCalls = numberOfBufferedCalls; }

    public long getNumberOfFailedCalls() { return numberOfFailedCalls; }
    public void setNumberOfFailedCalls(long numberOfFailedCalls) { this.numberOfFailedCalls = numberOfFailedCalls; }

    public long getNumberOfSuccessfulCalls() { return numberOfSuccessfulCalls; }
    public void setNumberOfSuccessfulCalls(long numberOfSuccessfulCalls) { this.numberOfSuccessfulCalls = numberOfSuccessfulCalls; }

    public long getNumberOfNotPermittedCalls() { return numberOfNotPermittedCalls; }
    public void setNumberOfNotPermittedCalls(long numberOfNotPermittedCalls) { this.numberOfNotPermittedCalls = numberOfNotPermittedCalls; }
}
