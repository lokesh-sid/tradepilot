package tradingbot.agent.domain.model;

/**
 * AgentGoal - What the agent is trying to achieve
 */
public class AgentGoal {
    
    private final GoalType type;
    private final String description;
    
    public AgentGoal(GoalType type, String description) {
        this.type = type;
        this.description = description;
    }
    
    public GoalType getType() { return type; }
    public String getDescription() { return description; }
    
    @Override
    public String toString() {
        return type + ": " + description;
    }
    
    /**
     * Types of agent goals
     */
    public enum GoalType {
        MAXIMIZE_PROFIT,     // Pure profit seeking
        HEDGE_RISK,          // Risk management focus
        ACCUMULATE_ASSET,    // Long-term accumulation
        ARBITRAGE,           // Exploit price differences
        MARKET_MAKING        // Provide liquidity
    }
}
