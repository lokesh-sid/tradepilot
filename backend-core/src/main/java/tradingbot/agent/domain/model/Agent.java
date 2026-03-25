package tradingbot.agent.domain.model;

import java.time.Instant;

/**
 * Agent - Aggregate root for autonomous trading agent
 * 
 * MVP: Simplified version with basic sense-think cycle
 */
public class Agent {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Agent{");
        sb.append("id=").append(id).append(", ");
        sb.append("name='").append(name).append("', ");
        sb.append("goal=").append(goal).append(", ");
        sb.append("tradingSymbol='").append(tradingSymbol).append("', ");
        sb.append("capital=").append(capital).append(", ");
        sb.append("state=").append(state).append(", ");
        sb.append("createdAt=").append(createdAt).append(", ");
        sb.append("ownerId='").append(ownerId).append("', ");
        sb.append("exchangeName='").append(exchangeName).append("'}");
        return sb.toString();
    }
    
    private final AgentId id;
    private final String name;
    private AgentGoal goal;
    private final String tradingSymbol;
    private final double capital;
    private final AgentState state;
    private final Instant createdAt;
    private final String ownerId;
    private final String exchangeName;

    // Last perception and reasoning
    private Perception lastPerception;
    private Reasoning lastReasoning;
    
    public Agent(AgentId id, String name, AgentGoal goal, String tradingSymbol,
                double capital, AgentState state, Instant createdAt, String ownerId) {
        this(id, name, goal, tradingSymbol, capital, state, createdAt, ownerId, null);
    }

    public Agent(AgentId id, String name, AgentGoal goal, String tradingSymbol,
                double capital, AgentState state, Instant createdAt, String ownerId, String exchangeName) {
        this.id = id;
        this.name = name;
        this.goal = goal;
        this.tradingSymbol = tradingSymbol;
        this.capital = capital;
        this.state = state;
        this.createdAt = createdAt;
        this.ownerId = ownerId;
        this.exchangeName = exchangeName;
    }

    public Agent(AgentId id, String name, AgentGoal goal, String tradingSymbol,
                double capital, AgentState state, Instant createdAt) {
        this(id, name, goal, tradingSymbol, capital, state, createdAt, null, null);
    }

    /**
     * Factory method to create a new agent
     */
    public static Agent create(String name, AgentGoal goal, String tradingSymbol, double capital, String ownerId) {
        return create(name, goal, tradingSymbol, capital, ownerId, null);
    }

    public static Agent create(String name, AgentGoal goal, String tradingSymbol, double capital, String ownerId, String exchangeName) {
        return new Agent(
            AgentId.generate(),
            name,
            goal,
            tradingSymbol,
            capital,
            AgentState.createIdle(),
            Instant.now(),
            ownerId,
            exchangeName
        );
    }
    
    /**
     * SENSE: Agent perceives the market
     */
    public void perceive(Perception perception) {
        this.lastPerception = perception;
        this.state.incrementIteration();
    }
    
    /**
     * THINK: Agent reasons about what it perceives
     */
    public void reason(Reasoning reasoning) {
        this.lastReasoning = reasoning;
    }
    
    /**
     * Lifecycle methods
     */
    public void activate() {
        this.state.activate();
    }
    
    public void pause() {
        this.state.pause();
    }
    
    public void stop() {
        this.state.stop();
    }
    
    public void setGoal(AgentGoal goal) {
        this.goal = goal;
    }
    
    // Getters
    public AgentId getId() { return id; }
    public String getName() { return name; }
    public AgentGoal getGoal() { return goal; }
    public String getTradingSymbol() { return tradingSymbol; }
    public double getCapital() { return capital; }
    public AgentState getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
    public String getOwnerId() { return ownerId; }
    public String getExchangeName() { return exchangeName; }
    public Perception getLastPerception() { return lastPerception; }
    public Reasoning getLastReasoning() { return lastReasoning; }
}
