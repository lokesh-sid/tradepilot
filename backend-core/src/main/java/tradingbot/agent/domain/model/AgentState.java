package tradingbot.agent.domain.model;

import java.time.Instant;

/**
 * AgentState - Current state of the agent
 */
public class AgentState {
    
    private Status status;
    private Instant lastActiveAt;
    private int iterationCount;
    
    public AgentState(Status status, Instant lastActiveAt, int iterationCount) {
        this.status = status;
        this.lastActiveAt = lastActiveAt;
        this.iterationCount = iterationCount;
    }
    
    /**
     * Create initial idle state
     */
    public static AgentState createIdle() {
        return new AgentState(Status.IDLE, null, 0);
    }
    
    /**
     * Update to active status
     */
    public void activate() {
        this.status = Status.ACTIVE;
        this.lastActiveAt = Instant.now();
    }
    
    /**
     * Update to paused status
     */
    public void pause() {
        this.status = Status.PAUSED;
    }
    
    /**
     * Update to stopped status
     */
    public void stop() {
        this.status = Status.STOPPED;
    }
    
    /**
     * Increment iteration count
     */
    public void incrementIteration() {
        this.iterationCount++;
        this.lastActiveAt = Instant.now();
    }
    
    public Status getStatus() { return status; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public int getIterationCount() { return iterationCount; }
    
    /**
     * Agent status
     */
    public enum Status {
        IDLE,      // Created but not started
        ACTIVE,    // Running
        PAUSED,    // Temporarily stopped
        STOPPED    // Permanently stopped
    }
}
