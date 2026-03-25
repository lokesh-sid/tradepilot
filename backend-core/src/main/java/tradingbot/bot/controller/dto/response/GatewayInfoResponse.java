package tradingbot.bot.controller.dto.response;

import java.util.Map;

/**
 * Response for gateway information API
 */
public class GatewayInfoResponse {
    private String name;
    private String version;
    private String description;
    private Map<String, Boolean> features;

    public GatewayInfoResponse() {}

    public GatewayInfoResponse(String name, String version, String description, Map<String, Boolean> features) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.features = features;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Boolean> getFeatures() { return features; }
    public void setFeatures(Map<String, Boolean> features) { this.features = features; }
}
