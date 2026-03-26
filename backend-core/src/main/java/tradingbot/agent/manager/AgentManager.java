package tradingbot.agent.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import tradingbot.agent.ReactiveTradingAgent;
import tradingbot.agent.TradingAgent;
import tradingbot.agent.application.AgentOrchestrator;
import tradingbot.agent.application.event.AgentPausedEvent;
import tradingbot.agent.application.event.AgentStartedEvent;
import tradingbot.agent.application.event.AgentStoppedEvent;
import tradingbot.agent.domain.model.AgentId;
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
    private final ApplicationEventPublisher eventPublisher;

    public AgentManager(JpaAgentRepository agentRepository,
                        AgentFactory agentFactory,
                        AgentOrchestrator agentOrchestrator,
                        ApplicationEventPublisher eventPublisher) {
        this.agentRepository = agentRepository;
        this.agentFactory = agentFactory;
        this.agentOrchestrator = agentOrchestrator;
        this.eventPublisher = eventPublisher;
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
                    publishStartedEvent(entity);
                }
            } catch (Exception e) {
                log.error("Failed to load agent: {}", entity.getId(), e);
            }
        }
        log.info("Loaded {} agents", agents.size());
    }

    public void registerAgent(TradingAgent agent) {
        agents.put(agent.getId(), agent);
    }

    // ------------------------------------------------------------------
    // Lifecycle — simple start (keeps existing executionMode)
    // ------------------------------------------------------------------

    public void startAgent(String id) {
        TradingAgent agent = agents.get(id);
        if (agent == null) {
            log.warn("startAgent: agent not found: {}", id);
            return;
        }
        if (!agent.isRunning()) {
            agent.start();
        }
        agentRepository.updateStatus(id, AgentEntity.AgentStatus.ACTIVE);
        agentRepository.findById(id).ifPresent(this::publishStartedEvent);
        log.info("Agent {} started", id);
    }

    // ------------------------------------------------------------------
    // Lifecycle — start with executionMode change (used by BotStateController)
    // ------------------------------------------------------------------

    public void startAgent(String id, AgentEntity.ExecutionMode mode) {
        agentRepository.updateStatusAndMode(id, AgentEntity.AgentStatus.ACTIVE, mode);
        refreshAgent(id);
        startAgent(id);
    }

    // ------------------------------------------------------------------
    // Lifecycle — stop
    // ------------------------------------------------------------------

    public void stopAgent(String id) {
        TradingAgent agent = agents.get(id);
        if (agent == null) {
            log.warn("stopAgent: agent not found: {}", id);
            return;
        }
        if (agent.isRunning()) {
            agent.stop();
        }
        agentRepository.updateStatus(id, AgentEntity.AgentStatus.STOPPED);
        eventPublisher.publishEvent(new AgentStoppedEvent(new AgentId(id)));
        log.info("Agent {} stopped", id);
    }

    // ------------------------------------------------------------------
    // Lifecycle — pause (halts dispatch without tearing down the agent)
    // ------------------------------------------------------------------

    public void pauseAgent(String id) {
        TradingAgent agent = agents.get(id);
        if (agent == null) {
            log.warn("pauseAgent: agent not found: {}", id);
            return;
        }
        if (agent instanceof ReactiveTradingAgent reactive) {
            reactive.pause();
        } else {
            // Non-reactive agents have no pause — stop is the fallback
            agent.stop();
        }
        agentRepository.updateStatus(id, AgentEntity.AgentStatus.PAUSED);
        eventPublisher.publishEvent(new AgentPausedEvent(new AgentId(id)));
        log.info("Agent {} paused", id);
    }

    // ------------------------------------------------------------------
    // Lifecycle — resume from pause
    // ------------------------------------------------------------------

    public void resumeAgent(String id) {
        TradingAgent agent = agents.get(id);
        if (agent == null) {
            log.warn("resumeAgent: agent not found: {}", id);
            return;
        }
        if (agent instanceof ReactiveTradingAgent reactive) {
            reactive.resume();
        } else {
            agent.start();
        }
        agentRepository.updateStatus(id, AgentEntity.AgentStatus.ACTIVE);
        agentRepository.findById(id).ifPresent(this::publishStartedEvent);
        log.info("Agent {} resumed", id);
    }

    // ------------------------------------------------------------------
    // Bulk operations
    // ------------------------------------------------------------------

    public void startAll() {
        agents.keySet().forEach(this::startAgent);
    }

    public void stopAll() {
        agents.keySet().forEach(this::stopAgent);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down all agents...");
        // Stop in-memory agents without updating DB status so they resume on restart.
        agents.values().forEach(TradingAgent::stop);
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    public TradingAgent createAgent(AgentEntity entity) {
        agentRepository.save(entity);
        TradingAgent agent = agentFactory.createAgent(entity);
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
        eventPublisher.publishEvent(new AgentStoppedEvent(new AgentId(id)));
    }

    public void refreshAgent(String id) {
        agentOrchestrator.deregisterReactiveAgent(id);
        TradingAgent existing = agents.get(id);
        if (existing != null && existing.isRunning()) {
            existing.stop();
        }
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

    public TradingAgent getAgent(String id) {
        return agents.get(id);
    }

    public List<TradingAgent> getAgents() {
        return new ArrayList<>(agents.values());
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private void publishStartedEvent(AgentEntity entity) {
        eventPublisher.publishEvent(new AgentStartedEvent(
                new AgentId(entity.getId()), entity.getTradingSymbol()));
    }
}
