package tradingbot.bot.controller.dto.response;

/**
 * Response for rate limiter metrics API
 */
public class RateLimiterMetricsResponse {
    private RateLimiterMetrics trading;
    private RateLimiterMetrics market;
    private RateLimiterMetrics account;

    public RateLimiterMetricsResponse() {}

    public RateLimiterMetricsResponse(RateLimiterMetrics trading, RateLimiterMetrics market, RateLimiterMetrics account) {
        this.trading = trading;
        this.market = market;
        this.account = account;
    }

    public RateLimiterMetrics getTrading() { return trading; }
    public void setTrading(RateLimiterMetrics trading) { this.trading = trading; }

    public RateLimiterMetrics getMarket() { return market; }
    public void setMarket(RateLimiterMetrics market) { this.market = market; }

    public RateLimiterMetrics getAccount() { return account; }
    public void setAccount(RateLimiterMetrics account) { this.account = account; }

    public static class RateLimiterMetrics {
        private int availablePermissions;
        private int numberOfWaitingThreads;

        public RateLimiterMetrics() {}

        public RateLimiterMetrics(int availablePermissions, int numberOfWaitingThreads) {
            this.availablePermissions = availablePermissions;
            this.numberOfWaitingThreads = numberOfWaitingThreads;
        }

        public int getAvailablePermissions() { return availablePermissions; }
        public void setAvailablePermissions(int availablePermissions) { this.availablePermissions = availablePermissions; }

        public int getNumberOfWaitingThreads() { return numberOfWaitingThreads; }
        public void setNumberOfWaitingThreads(int numberOfWaitingThreads) { this.numberOfWaitingThreads = numberOfWaitingThreads; }
    }
}
