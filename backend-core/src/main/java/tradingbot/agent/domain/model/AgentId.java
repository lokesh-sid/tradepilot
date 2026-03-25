package tradingbot.agent.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * AgentId - Value object representing unique agent identifier
 */
public class AgentId {
    
    private final String value;
    
    public AgentId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AgentId cannot be null or empty");
        }
        this.value = value;
    }
    
    /**
     * Generate a new unique AgentId
     */
    public static AgentId generate() {
        return new AgentId(UUID.randomUUID().toString());
    }
    
    public String getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentId agentId = (AgentId) o;
        return Objects.equals(value, agentId.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
    
    @Override
    public String toString() {
        return value;
    }
}
