package tradingbot.agent.api.dto;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentGoal;
import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.Reasoning;

/**
 * AgentDTOMapper - Maps between Agent domain model and DTOs
 */
public class AgentDTOMapper {
    
    /**
     * Convert Agent domain model to AgentResponse DTO
     */
    public static AgentResponse toResponse(Agent agent) {
        AgentResponse.PerceptionDTO perceptionDTO = null;
        if (agent.getLastPerception() != null) {
            Perception p = agent.getLastPerception();
            perceptionDTO = new AgentResponse.PerceptionDTO(
                p.getSymbol(),
                p.getCurrentPrice(),
                p.getTrend(),
                p.getSentiment(),
                p.getVolume(),
                p.getTimestamp()
            );
        }
        
        AgentResponse.ReasoningDTO reasoningDTO = null;
        if (agent.getLastReasoning() != null) {
            Reasoning r = agent.getLastReasoning();
            reasoningDTO = new AgentResponse.ReasoningDTO(
                r.getObservation(),
                r.getAnalysis(),
                r.getRiskAssessment(),
                r.getRecommendation(),
                r.getConfidence(),
                r.getTimestamp()
            );
        }
        
        return new AgentResponse(
            agent.getId().getValue(),
            agent.getName(),
            agent.getGoal().getType().name(),
            agent.getGoal().getDescription(),
            agent.getTradingSymbol(),
            agent.getCapital(),
            agent.getState().getStatus().name(),
            agent.getCreatedAt(),
            agent.getState().getLastActiveAt(),
            agent.getState().getIterationCount(),
            perceptionDTO,
            reasoningDTO,
            System.currentTimeMillis(),
            java.util.UUID.randomUUID().toString(),
            agent.getExchangeName(),
            true
        );
    }
    
    /**
     * Convert CreateAgentRequest to Agent domain model
     */
    public static Agent toDomain(CreateAgentRequest request, String ownerId) {
        AgentGoal.GoalType goalType = AgentGoal.GoalType.valueOf(request.goalType());
        AgentGoal goal = new AgentGoal(goalType, request.goalDescription());
        
        return Agent.create(
            request.name(),
            goal,
            request.tradingSymbol(),
            request.capital(),
            ownerId,
            request.exchangeName()
        );
    }
}
