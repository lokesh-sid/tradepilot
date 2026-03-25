package tradingbot.agent.config;

public class ExchangeCredentials {
    private String apiKey;
    private String apiSecret;
    private String domain; // For Bybit, optional
    private String network; // For dYdX, optional
    private String mainnetUrl; // For dYdX, optional
    private String testnetUrl; // For dYdX, optional
    private String privateKey; // For dYdX, optional

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getApiSecret() { return apiSecret; }
    public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getNetwork() { return network; }
    public void setNetwork(String network) { this.network = network; }
    public String getMainnetUrl() { return mainnetUrl; }
    public void setMainnetUrl(String mainnetUrl) { this.mainnetUrl = mainnetUrl; }
    public String getTestnetUrl() { return testnetUrl; }
    public void setTestnetUrl(String testnetUrl) { this.testnetUrl = testnetUrl; }
    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
}
