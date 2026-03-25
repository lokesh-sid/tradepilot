package tradingbot.agent.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "trading")

public class AgentProperties {
    private List<AgentConfig> agents;
    private Map<String, ExchangeCredentials> credentials;

    public List<AgentConfig> getAgents() {
        return agents;
    }
    public void setAgents(List<AgentConfig> agents) {
        this.agents = agents;
    }

    public Map<String, ExchangeCredentials> getCredentials() {
        return credentials;
    }
    public void setCredentials(Map<String, ExchangeCredentials> credentials) {
        this.credentials = credentials;
    }

    public static class AgentConfig {
        private String exchange;
        private String symbol;
        private String interval;
        private String strategy;
        private String direction; // BUY, SELL, LONG, SHORT, etc.

        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }
        public String getInterval() { return interval; }
        public void setInterval(String interval) { this.interval = interval; }
        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
    }
}
