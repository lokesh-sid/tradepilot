package tradingbot.agent.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AgentStateTest {
    
    @Test
    void testCreateIdle() {
        // When
        AgentState state = AgentState.createIdle();
        
        // Then
        assertEquals(AgentState.Status.IDLE, state.getStatus());
        assertNull(state.getLastActiveAt());
        assertEquals(0, state.getIterationCount());
    }
    
    @Test
    void testActivate() {
        // Given
        AgentState state = AgentState.createIdle();
        
        // When
        state.activate();
        
        // Then
        assertEquals(AgentState.Status.ACTIVE, state.getStatus());
        assertNotNull(state.getLastActiveAt());
    }
    
    @Test
    void testPause() {
        // Given
        AgentState state = AgentState.createIdle();
        state.activate();
        
        // When
        state.pause();
        
        // Then
        assertEquals(AgentState.Status.PAUSED, state.getStatus());
    }
    
    @Test
    void testStop() {
        // Given
        AgentState state = AgentState.createIdle();
        state.activate();
        
        // When
        state.stop();
        
        // Then
        assertEquals(AgentState.Status.STOPPED, state.getStatus());
    }
    
    @Test
    void testIncrementIteration() {
        // Given
        AgentState state = AgentState.createIdle();
        assertEquals(0, state.getIterationCount());
        
        // When
        state.incrementIteration();
        
        // Then
        assertEquals(1, state.getIterationCount());
        assertNotNull(state.getLastActiveAt());
        
        // When - increment again
        state.incrementIteration();
        
        // Then
        assertEquals(2, state.getIterationCount());
    }
    
    @Test
    void testMultipleIterations() {
        // Given
        AgentState state = AgentState.createIdle();
        
        // When - 10 iterations
        for (int i = 0; i < 10; i++) {
            state.incrementIteration();
        }
        
        // Then
        assertEquals(10, state.getIterationCount());
    }
}
