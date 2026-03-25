package tradingbot.agent.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import tradingbot.agent.ReactiveTradingAgent;
import tradingbot.agent.TradingAgent;
import tradingbot.agent.application.AgentOrchestrator;
import tradingbot.agent.factory.AgentFactory;
import tradingbot.agent.infrastructure.repository.AgentEntity;
import tradingbot.agent.infrastructure.repository.JpaAgentRepository;
@Service
public class AgentManager {
    private static final Logger log = LoggerFactory.getLogger(AgentManager.class);

    private final Map<String, TradingAgent> agents = new ConcurrentHashMap<>();

    private final JpaAgentRepository agentRepository;
    private final AgentFactory agentFactory;
    private final AgentOrchestrator agentOrchestrator;

    public AgentManager(JpaAgentRepository agentRepository, AgentFactory agentFactory,
                        AgentOrchestrator agentOrchestrator) {
        this.agentRepository = agentRepository;
        this.agentFactory = agentFactory;
        this.agentOrchestrator = agentOrchestrator;
    }

    @PostConstruct
    public void loadAgents() {
        log.info("Loading agents from database...");
        List<AgentEntity> entities = agentRepository.findAll();
        for (AgentEntity entity : entities) {
            try {
                TradingAgent agent = agentFactory.createAgent(entity);
                if (agent == null) {
                    log.warn("Skipping agent {} — factory returned null (check goalDescription JSON)", entity.getId());
                    continue;
                }
                agents.put(agent.getId(), agent);
                if (agent instanceof ReactiveTradingAgent reactive) {
                    agentOrchestrator.registerReactiveAgent(reactive);
                }
                if (entity.getStatus() == AgentEntity.AgentStatus.ACTIVE) {
                    log.info("Starting agent: {}", agent.getName());
                    agent.start();
                }
            } catch (Exception e) {
                log.error("Failed to load agent: {}", entity.getId(), e);
            }
        }
        log.info("Loaded {} agents", agents.size());
    }

    public void registerAgent(TradingAgent agent) {
        agents.put(agent.getId(), agent);
        // Note: Persistence is handled by the creator of the agent (e.g. Controller/Service)
        // creating the AgentEntity first.
    }

    public void startAgent(String id) {
        TradingAgent agent = agents.get(id);
        if (agent != null) {
            if (!agent.isRunning()) {
                agent.start();
            }
            updateAgentStatus(id, AgentEntity.AgentStatus.ACTIVE);
            log.info("Agent {} started", id);
        } else {
            log.warn("Agent not found: {}", id);
        }
    }

    public void stopAgent(String id) {
        TradingAgent agent = agents.get(id);
        if (agent != null) {
            if (agent.isRunning()) {
                agent.stop();
                updateAgentStatus(id, AgentEntity.AgentStatus.STOPPED);
                log.info("Agent {} stopped", id);
            }
        } else {
            log.warn("Agent not found: {}", id);
        }
    }

    public void startAll() {
        agents.keySet().forEach(this::startAgent);
    }

    public void stopAll() {
        agents.keySet().forEach(this::stopAgent);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down all agents...");
        // We stop the agents but we might NOT want to update the DB status to STOPPED
        // so that they resume on restart.
        // However, the requirement says "Implement Graceful Shutdown".
        // If we update DB to STOPPED, they won't auto-start.
        // So we should just call agent.stop() without updating DB status, 
        // OR we rely on the fact that the process is dying.
        
        // Let's just stop the in-memory agents.
        agents.values().forEach(TradingAgent::stop);
    }

    public List<TradingAgent> getAgents() {
        return new ArrayList<>(agents.values());
    }
    private void updateAgentStatus(String id, AgentEntity.AgentStatus status) {
        agentRepository.findById(id).ifPresent(entity -> {
            AgentEntity updated = new AgentEntity.Builder()
                .id(entity.getId())
                .name(entity.getName())
                .goalType(entity.getGoalType())
                .goalDescription(entity.getGoalDescription())
                .tradingSymbol(entity.getTradingSymbol())
                .capital(entity.getCapital())
                .status(status)
                .createdAt(entity.getCreatedAt())
                .lastActiveAt(entity.getLastActiveAt())
                .iterationCount(entity.getIterationCount())
                .lastPrice(entity.getLastPrice())
                .lastTrend(entity.getLastTrend())
                .lastSentiment(entity.getLastSentiment())
                .lastVolume(entity.getLastVolume())
                .perceivedAt(entity.getPerceivedAt())
                .lastObservation(entity.getLastObservation())
                .lastAnalysis(entity.getLastAnalysis())
                .lastRiskAssessment(entity.getLastRiskAssessment())
                .lastRecommendation(entity.getLastRecommendation())
                .lastConfidence(entity.getLastConfidence())
                .reasonedAt(entity.getReasonedAt())
                .ownerId(entity.getOwnerId())
                .executionMode(entity.getExecutionMode())
                .build();
            agentRepository.saveAndFlush(updated);
        });
    }
    public TradingAgent createAgent(AgentEntity entity) {
        agentRepository.save(entity);
        TradingAgent agent = agentFactory.createAgent(entity); // throws RuntimeException on invalid config
        agents.put(agent.getId(), agent);
        if (agent instanceof ReactiveTradingAgent reactive) {
            agentOrchestrator.registerReactiveAgent(reactive);
        }
        return agent;
    }

    public void deleteAgent(String id) {
        TradingAgent agent = agents.remove(id);
        if (agent != null && agent.isRunning()) {
            agent.stop();
        }
        agentOrchestrator.deregisterReactiveAgent(id);
        agentRepository.deleteById(id);
    }

    public TradingAgent getAgent(String id) {
        return agents.get(id);
    }

    public void refreshAgent(String id) {
        stopAgent(id);
        agentOrchestrator.deregisterReactiveAgent(id);
        agentRepository.findById(id).ifPresent(entity -> {
            try {
                TradingAgent agent = agentFactory.createAgent(entity);
                agents.put(id, agent);
                if (agent instanceof ReactiveTradingAgent reactive) {
                    agentOrchestrator.registerReactiveAgent(reactive);
                }
            } catch (Exception e) {
                log.error("Failed to refresh agent: {}", id, e);
            }
        });
    }
}
