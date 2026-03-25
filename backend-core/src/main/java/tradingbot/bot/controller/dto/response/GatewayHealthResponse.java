package tradingbot.bot.controller.dto.response;

/**
 * Response for gateway health check API
 */
public class GatewayHealthResponse {
    private String status;
    private String gateway;
    private long timestamp;
    private String version;

    public GatewayHealthResponse() {}

    public GatewayHealthResponse(String status, String gateway, long timestamp, String version) {
        this.status = status;
        this.gateway = gateway;
        this.timestamp = timestamp;
        this.version = version;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
