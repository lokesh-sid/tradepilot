package tradingbot.bot.controller.dto.response;

/**
 * Response for retry metrics API
 */
public class RetryMetricsResponse {
    private long numberOfSuccessfulCallsWithoutRetryAttempt;
    private long numberOfSuccessfulCallsWithRetryAttempt;
    private long numberOfFailedCallsWithRetryAttempt;
    private long numberOfFailedCallsWithoutRetryAttempt;

    public RetryMetricsResponse() {}

    public RetryMetricsResponse(long numberOfSuccessfulCallsWithoutRetryAttempt,
                               long numberOfSuccessfulCallsWithRetryAttempt,
                               long numberOfFailedCallsWithRetryAttempt,
                               long numberOfFailedCallsWithoutRetryAttempt) {
        this.numberOfSuccessfulCallsWithoutRetryAttempt = numberOfSuccessfulCallsWithoutRetryAttempt;
        this.numberOfSuccessfulCallsWithRetryAttempt = numberOfSuccessfulCallsWithRetryAttempt;
        this.numberOfFailedCallsWithRetryAttempt = numberOfFailedCallsWithRetryAttempt;
        this.numberOfFailedCallsWithoutRetryAttempt = numberOfFailedCallsWithoutRetryAttempt;
    }

    public long getNumberOfSuccessfulCallsWithoutRetryAttempt() { return numberOfSuccessfulCallsWithoutRetryAttempt; }
    public void setNumberOfSuccessfulCallsWithoutRetryAttempt(long numberOfSuccessfulCallsWithoutRetryAttempt) { 
        this.numberOfSuccessfulCallsWithoutRetryAttempt = numberOfSuccessfulCallsWithoutRetryAttempt; 
    }

    public long getNumberOfSuccessfulCallsWithRetryAttempt() { return numberOfSuccessfulCallsWithRetryAttempt; }
    public void setNumberOfSuccessfulCallsWithRetryAttempt(long numberOfSuccessfulCallsWithRetryAttempt) { 
        this.numberOfSuccessfulCallsWithRetryAttempt = numberOfSuccessfulCallsWithRetryAttempt; 
    }

    public long getNumberOfFailedCallsWithRetryAttempt() { return numberOfFailedCallsWithRetryAttempt; }
    public void setNumberOfFailedCallsWithRetryAttempt(long numberOfFailedCallsWithRetryAttempt) { 
        this.numberOfFailedCallsWithRetryAttempt = numberOfFailedCallsWithRetryAttempt; 
    }

    public long getNumberOfFailedCallsWithoutRetryAttempt() { return numberOfFailedCallsWithoutRetryAttempt; }
    public void setNumberOfFailedCallsWithoutRetryAttempt(long numberOfFailedCallsWithoutRetryAttempt) { 
        this.numberOfFailedCallsWithoutRetryAttempt = numberOfFailedCallsWithoutRetryAttempt; 
    }
}
