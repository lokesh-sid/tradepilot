package tradingbot.agent.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class AgentTest {
    
    @Test
    void testCreateAgent() {
        // Given
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Maximize BTC profits");
        
        // When
        Agent agent = Agent.create("Bitcoin Trader", goal, "BTCUSDT", 10000.0, "test-user-id");
        
        // Then
        assertNotNull(agent);
        assertNotNull(agent.getId());
        assertEquals("Bitcoin Trader", agent.getName());
        assertEquals(goal, agent.getGoal());
        assertEquals("BTCUSDT", agent.getTradingSymbol());
        assertEquals(10000.0, agent.getCapital());
        assertEquals(AgentState.Status.IDLE, agent.getState().getStatus());
        assertNotNull(agent.getCreatedAt());
    }
    
    @Test
    void testAgentLifecycle() {
        // Given
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Test");
        Agent agent = Agent.create("Test Agent", goal, "BTCUSDT", 5000.0, "test-user-id");
        
        // When & Then - Activate
        agent.activate();
        assertEquals(AgentState.Status.ACTIVE, agent.getState().getStatus());
        assertNotNull(agent.getState().getLastActiveAt());
        
        // When & Then - Pause
        agent.pause();
        assertEquals(AgentState.Status.PAUSED, agent.getState().getStatus());
        
        // When & Then - Stop
        agent.stop();
        assertEquals(AgentState.Status.STOPPED, agent.getState().getStatus());
    }
    
    @Test
    void testAgentPerception() {
        // Given
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Test");
        Agent agent = Agent.create("Test Agent", goal, "BTCUSDT", 5000.0, "test-user-id");
        Perception perception = new Perception(
            "BTCUSDT", 45000.0, "UPTREND", "BULLISH", 1000000.0, Instant.now()
        );
        
        // When
        agent.perceive(perception);
        
        // Then
        assertEquals(perception, agent.getLastPerception());
        assertEquals(1, agent.getState().getIterationCount());
    }
    
    @Test
    void testAgentReasoning() {
        // Given
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Test");
        Agent agent = Agent.create("Test Agent", goal, "BTCUSDT", 5000.0, "test-user-id");
        Reasoning reasoning = new Reasoning(
            "Price rising",
            "Uptrend confirmed",
            "Low risk",
            "BUY",
            85,
            Instant.now()
        );
        
        // When
        agent.reason(reasoning);
        
        // Then
        assertEquals(reasoning, agent.getLastReasoning());
    }
    
    @Test
    void testSetGoal() {
        // Given
        AgentGoal initialGoal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Initial");
        Agent agent = Agent.create("Test Agent", initialGoal, "BTCUSDT", 5000.0, "test-user-id");
        AgentGoal newGoal = new AgentGoal(AgentGoal.GoalType.HEDGE_RISK, "Risk hedging");
        
        // When
        agent.setGoal(newGoal);
        
        // Then
        assertEquals(newGoal, agent.getGoal());
        assertEquals(AgentGoal.GoalType.HEDGE_RISK, agent.getGoal().getType());
    }
    
    @Test
    void testMultiplePerceptions() {
        // Given
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Test");
        Agent agent = Agent.create("Test Agent", goal, "BTCUSDT", 5000.0, "test-user-id");
        
        // When - Multiple perceptions
        Perception p1 = new Perception("BTCUSDT", 45000.0, "UPTREND", "BULLISH", 1000000.0, Instant.now());
        agent.perceive(p1);
        
        Perception p2 = new Perception("BTCUSDT", 46000.0, "UPTREND", "BULLISH", 1200000.0, Instant.now());
        agent.perceive(p2);
        
        Perception p3 = new Perception("BTCUSDT", 47000.0, "UPTREND", "VERY_BULLISH", 1500000.0, Instant.now());
        agent.perceive(p3);
        
        // Then
        assertEquals(p3, agent.getLastPerception());
        assertEquals(3, agent.getState().getIterationCount());
        assertEquals(47000.0, agent.getLastPerception().getCurrentPrice());
    }
}
