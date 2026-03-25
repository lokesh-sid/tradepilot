package tradingbot.agent.infrastructure.repository;

import org.springframework.stereotype.Component;

import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentGoal;
import tradingbot.agent.domain.model.AgentGoal.GoalType;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.model.AgentState;
import tradingbot.agent.domain.model.Perception;
import tradingbot.agent.domain.model.Reasoning;

/**
 * AgentMapper - Maps between Agent domain model and AgentEntity
 */
@Component
public class AgentMapper {
    
    /**
     * Convert Agent domain model to AgentEntity
     */
    public static AgentEntity toEntity(Agent agent) {
        AgentEntity.Builder builder = new AgentEntity.Builder()
            .id(agent.getId().getValue())
            .name(agent.getName())
            .goalType(agent.getGoal().getType().name())
            .goalDescription(agent.getGoal().getDescription())
            .tradingSymbol(agent.getTradingSymbol())
            .capital(agent.getCapital())
            .status(mapStatus(agent.getState().getStatus()))
            .createdAt(agent.getCreatedAt())
            .ownerId(agent.getOwnerId())
            .exchangeName(agent.getExchangeName())
            .executionMode(AgentEntity.ExecutionMode.NONE)
            .lastActiveAt(agent.getState().getLastActiveAt())
            .iterationCount(agent.getState().getIterationCount());

        // Map perception
        if (agent.getLastPerception() != null) {
            Perception perception = agent.getLastPerception();
            builder.lastPrice(perception.getCurrentPrice())
                   .lastTrend(perception.getTrend())
                   .lastSentiment(perception.getSentiment())
                   .lastVolume(perception.getVolume())
                   .perceivedAt(perception.getTimestamp());
        }

        // Map reasoning
        if (agent.getLastReasoning() != null) {
            Reasoning reasoning = agent.getLastReasoning();
            builder.lastObservation(reasoning.getObservation())
                   .lastAnalysis(reasoning.getAnalysis())
                   .lastRiskAssessment(reasoning.getRiskAssessment())
                   .lastRecommendation(reasoning.getRecommendation())
                   .lastConfidence(reasoning.getConfidence())
                   .reasonedAt(reasoning.getTimestamp());
        }

        return builder.build();
    }
    
    /**
     * Convert AgentEntity to Agent domain model
     */
    public static Agent toDomain(AgentEntity entity) {
        // goalType is now always a valid domain GoalType (FUTURES_PAPER/FUTURES are in executionMode).
        GoalType goalType = GoalType.valueOf(entity.getGoalType());
        AgentGoal goal = new AgentGoal(goalType, entity.getGoalDescription());
        
        // Create state
        AgentState state = new AgentState(
            mapStatus(entity.getStatus()),
            entity.getLastActiveAt(),
            entity.getIterationCount()
        );
        
        // Create perception (if exists)
        Perception perception = null;
        if (entity.getLastPrice() != null) {
            perception = new Perception(
                entity.getTradingSymbol(),
                entity.getLastPrice(),
                entity.getLastTrend(),
                entity.getLastSentiment(),
                entity.getLastVolume() != null ? entity.getLastVolume() : 0.0,
                entity.getPerceivedAt()
            );
        }
        
        // Create reasoning (if exists)
        Reasoning reasoning = null;
        if (entity.getLastObservation() != null) {
            reasoning = new Reasoning(
                entity.getLastObservation(),
                entity.getLastAnalysis(),
                entity.getLastRiskAssessment(),
                entity.getLastRecommendation(),
                entity.getLastConfidence() != null ? entity.getLastConfidence() : 0,
                entity.getReasonedAt()
            );
        }
        
        // Create agent
        Agent agent = new Agent(
            new AgentId(entity.getId()),
            entity.getName(),
            goal,
            entity.getTradingSymbol(),
            entity.getCapital(),
            state,
            entity.getCreatedAt(),
            entity.getOwnerId(),
            entity.getExchangeName()
        );
        
        // Set perception and reasoning if they exist
        if (perception != null) {
            agent.perceive(perception);
        }
        if (reasoning != null) {
            agent.reason(reasoning);
        }
        
        return agent;
    }
    
    /**
     * Map domain AgentState.Status to entity AgentStatus
     */
    private static AgentEntity.AgentStatus mapStatus(AgentState.Status domainStatus) {
        return switch (domainStatus) {
            case IDLE -> AgentEntity.AgentStatus.IDLE;
            case ACTIVE -> AgentEntity.AgentStatus.ACTIVE;
            case PAUSED -> AgentEntity.AgentStatus.PAUSED;
            case STOPPED -> AgentEntity.AgentStatus.STOPPED;
        };
    }
    
    /**
     * Map entity AgentStatus to domain AgentState.Status
     */
    private static AgentState.Status mapStatus(AgentEntity.AgentStatus entityStatus) {
        return switch (entityStatus) {
            case IDLE -> AgentState.Status.IDLE;
            case ACTIVE -> AgentState.Status.ACTIVE;
            case PAUSED -> AgentState.Status.PAUSED;
            case STOPPED -> AgentState.Status.STOPPED;
        };
    }
}
