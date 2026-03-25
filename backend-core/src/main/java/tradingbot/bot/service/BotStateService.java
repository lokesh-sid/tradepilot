package tradingbot.bot.service;

// import static tradingbot.agent.persistence.LegacyAgentEntity.AgentType.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tradingbot.agent.infrastructure.repository.AgentEntity;
import tradingbot.agent.infrastructure.repository.JpaAgentRepository;
import tradingbot.agent.manager.AgentManager;
import tradingbot.bot.TradeDirection;

@Service
public class BotStateService {

    private static final Logger logger = LoggerFactory.getLogger(BotStateService.class);

    private final AgentManager agentManager;
    private final JpaAgentRepository agentRepository;

    // ✅ Constructor injection only — no runtime parameter passing
    public BotStateService(AgentManager agentManager, JpaAgentRepository agentRepository) {
        this.agentManager = agentManager;
        this.agentRepository = agentRepository;
    }

    @Transactional
    public void startBot(String botId, boolean resolvedPaperMode, TradeDirection direction) {
        agentRepository.findById(botId).ifPresent(entity -> {
            AgentEntity updated = new AgentEntity.Builder()
                .id(entity.getId())
                .name(entity.getName())
                .goalType(entity.getGoalType())
                .goalDescription(entity.getGoalDescription())
                .tradingSymbol(entity.getTradingSymbol())
                .capital(entity.getCapital())
                .status(AgentEntity.AgentStatus.ACTIVE)
                .createdAt(entity.getCreatedAt())
                .ownerId(entity.getOwnerId())
                .executionMode(resolvedPaperMode ? AgentEntity.ExecutionMode.FUTURES_PAPER : AgentEntity.ExecutionMode.FUTURES)
                .build();
            agentRepository.save(updated);
        });

        agentManager.refreshAgent(botId);
        agentManager.startAgent(botId);

        logger.info("Bot {} started — paperMode={}, direction={}", botId, resolvedPaperMode, direction);
    }

    @Transactional
    public void stopBot(String botId) {
        agentManager.stopAgent(botId);
        agentRepository.findById(botId).ifPresent(entity -> {
            AgentEntity updated = new AgentEntity.Builder()
                .id(entity.getId())
                .name(entity.getName())
                .goalType(entity.getGoalType())
                .goalDescription(entity.getGoalDescription())
                .tradingSymbol(entity.getTradingSymbol())
                .capital(entity.getCapital())
                .status(AgentEntity.AgentStatus.STOPPED)
                .createdAt(entity.getCreatedAt())
                .ownerId(entity.getOwnerId())
                .executionMode(entity.getExecutionMode())
                .build();
            agentRepository.save(updated);
        });

        logger.info("Bot {} stopped", botId);
    }

    @Transactional
    public void pauseBot(String botId) {
        agentManager.stopAgent(botId);
        agentRepository.findById(botId).ifPresent(entity -> {
            AgentEntity updated = new AgentEntity.Builder()
                .id(entity.getId())
                .name(entity.getName())
                .goalType(entity.getGoalType())
                .goalDescription(entity.getGoalDescription())
                .tradingSymbol(entity.getTradingSymbol())
                .capital(entity.getCapital())
                .status(AgentEntity.AgentStatus.PAUSED)
                .createdAt(entity.getCreatedAt())
                .ownerId(entity.getOwnerId())
                .executionMode(entity.getExecutionMode())
                .build();
            agentRepository.save(updated);
        });

        logger.info("Bot {} paused", botId);
    }
}