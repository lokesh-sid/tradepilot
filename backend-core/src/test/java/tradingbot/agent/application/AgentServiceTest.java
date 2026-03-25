package tradingbot.agent.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import tradingbot.agent.api.dto.AgentMapper;
import tradingbot.agent.api.dto.AgentResponse;
import tradingbot.agent.api.dto.CreateAgentRequest;
import tradingbot.agent.api.dto.PaginatedAgentResponse;
import tradingbot.agent.application.event.AgentPausedEvent;
import tradingbot.agent.application.event.AgentStartedEvent;
import tradingbot.agent.application.event.AgentStoppedEvent;
import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentGoal;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.model.AgentState;
import tradingbot.agent.domain.model.PageResult;
import tradingbot.agent.domain.repository.AgentRepository;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {
    
    @Mock
    private AgentRepository agentRepository;
    
    @Mock
    private AgentMapper agentMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;
    
    @InjectMocks
    private AgentService agentService;
    
    private CreateAgentRequest createRequest;
    private Agent testAgent;
    
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;
    
    @BeforeEach
    void setUp() {
        createRequest = new CreateAgentRequest(
            "Test Agent",
            "MAXIMIZE_PROFIT",
            "Maximize BTC profits",
            "BTCUSDT",
            10000.0,
        null
        );
        
        AgentGoal goal = new AgentGoal(AgentGoal.GoalType.MAXIMIZE_PROFIT, "Maximize BTC profits");
        testAgent = Agent.create("Test Agent", goal, "BTCUSDT", 10000.0, "user1");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }
    
    @Test
    void testCreateAgent_Success() {
        // Given
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("user1");
        SecurityContextHolder.setContext(securityContext);

        when(agentRepository.existsByName("Test Agent")).thenReturn(false);
        when(agentMapper.toDomain(createRequest, "user1")).thenReturn(testAgent);
        when(agentRepository.save(any(Agent.class))).thenReturn(testAgent);
        
        // When
        Agent result = agentService.createAgent(createRequest);
        
        // Then
        assertNotNull(result);
        assertEquals("Test Agent", result.getName());
        verify(agentRepository).existsByName("Test Agent");
        verify(agentMapper).toDomain(createRequest, "user1");
        verify(agentRepository).save(any(Agent.class));
    }
    
    @Test
    void testCreateAgent_AlreadyExists() {
        // Given
        when(agentRepository.existsByName("Test Agent")).thenReturn(true);
        
        // When & Then
        assertThrows(AgentAlreadyExistsException.class, () -> {
            agentService.createAgent(createRequest);
        });
        
        verify(agentRepository).existsByName("Test Agent");
        verify(agentRepository, never()).save(any(Agent.class));
    }
    
    // getAllAgents() is deprecated; use getAgentsByOwner()

    @Test
    void testGetAgentsByOwner() {
        // Given
        Agent agent1 = testAgent;
        PageResult<Agent> pageResult = new PageResult<>(List.of(agent1), 1L);
        when(agentRepository.findByOwner("user1", 0, 20)).thenReturn(pageResult);
        AgentResponse mockResponse = mock(AgentResponse.class);
        when(agentMapper.toResponse(agent1)).thenReturn(mockResponse);

        // When
        PaginatedAgentResponse result = agentService.getAgentsByOwner("user1", 0, 20);

        // Then
        assertEquals(1, result.content().size());
        assertEquals(1L, result.totalElements());
        verify(agentRepository).findByOwner("user1", 0, 20);
    }
    
    @Test
    void testGetAgent_Success() {
        // Given
        AgentId agentId = testAgent.getId();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(testAgent));
        
        // When
        Agent result = agentService.getAgent(agentId);
        
        // Then
        assertNotNull(result);
        assertEquals(testAgent.getId(), result.getId());
        verify(agentRepository).findById(agentId);
    }
    
    @Test
    void testGetAgent_NotFound() {
        // Given
        AgentId agentId = AgentId.generate();
        when(agentRepository.findById(agentId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(AgentNotFoundException.class, () -> {
            agentService.getAgent(agentId);
        });
        
        verify(agentRepository).findById(agentId);
    }
    
    @Test
    void testActivateAgent() {
        // Given
        AgentId agentId = testAgent.getId();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(testAgent));
        when(agentRepository.save(testAgent)).thenReturn(testAgent);
        
        // When
        Agent result = agentService.activateAgent(agentId);
        
        // Then
        assertEquals(AgentState.Status.ACTIVE, result.getState().getStatus());
        verify(agentRepository).findById(agentId);
        verify(agentRepository).save(testAgent);
        verify(eventPublisher).publishEvent(any(AgentStartedEvent.class));
    }
    
    @Test
    void testPauseAgent() {
        // Given
        AgentId agentId = testAgent.getId();
        testAgent.activate();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(testAgent));
        when(agentRepository.save(testAgent)).thenReturn(testAgent);
        
        // When
        Agent result = agentService.pauseAgent(agentId);
        
        // Then
        assertEquals(AgentState.Status.PAUSED, result.getState().getStatus());
        verify(agentRepository).findById(agentId);
        verify(agentRepository).save(testAgent);
        verify(eventPublisher).publishEvent(any(AgentPausedEvent.class));
    }
    
    @Test
    void testStopAgent() {
        // Given
        AgentId agentId = testAgent.getId();
        testAgent.activate();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(testAgent));
        when(agentRepository.save(testAgent)).thenReturn(testAgent);
        
        // When
        agentService.stopAgent(agentId);
        
        // Then
        assertEquals(AgentState.Status.STOPPED, testAgent.getState().getStatus());
        verify(agentRepository).findById(agentId);
        verify(agentRepository).save(testAgent);
        verify(eventPublisher).publishEvent(any(AgentStoppedEvent.class));
    }
}
