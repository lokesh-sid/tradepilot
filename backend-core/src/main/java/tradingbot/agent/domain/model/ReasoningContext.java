package tradingbot.agent.domain.model;

/**
 * ReasoningContext - Context provided to LLM for reasoning
 */
public class ReasoningContext {
    
    private final AgentGoal goal;
    private final Perception perception;
    private final String tradingSymbol;
    private final double capital;
    private final int iterationCount;
    
    public ReasoningContext(AgentGoal goal, Perception perception, String tradingSymbol,
                           double capital, int iterationCount) {
        this.goal = goal;
        this.perception = perception;
        this.tradingSymbol = tradingSymbol;
        this.capital = capital;
        this.iterationCount = iterationCount;
    }
    
    public AgentGoal getGoal() { return goal; }
    public Perception getPerception() { return perception; }
    public String getTradingSymbol() { return tradingSymbol; }
    public double getCapital() { return capital; }
    public int getIterationCount() { return iterationCount; }
}
