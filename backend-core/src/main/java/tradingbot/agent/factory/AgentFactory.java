package tradingbot.agent.factory;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import tradingbot.agent.ReactiveTradingAgent;
import tradingbot.agent.TradingAgent;
import tradingbot.agent.config.AgentProperties;
import tradingbot.agent.domain.risk.RiskContext;
import tradingbot.agent.domain.risk.RiskGuard;
import tradingbot.agent.domain.util.Ids;
import tradingbot.agent.impl.TechnicalTradingAgent;
import tradingbot.agent.infrastructure.repository.AgentEntity;
import tradingbot.config.TradingConfig;

/**
 * Factory component responsible for instantiating TradingAgent objects.
 *
 * <p>All execution modes (FUTURES, FUTURES_PAPER, NONE) produce a
 * {@link TechnicalTradingAgent}. The paper vs live distinction is handled at the
 * {@link tradingbot.agent.domain.execution.OrderExecutionGateway} level, resolved
 * by {@link tradingbot.agent.config.OrderExecutionGatewayRegistry} at dispatch time.
 */
@Component
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;
    private final RiskGuard riskGuard;

    public AgentFactory(ObjectMapper objectMapper,
                        AgentProperties agentProperties,
                        RiskGuard riskGuard) {
        this.objectMapper = objectMapper;
        this.agentProperties = agentProperties;
        this.riskGuard = riskGuard;
    }

    /**
     * Creates all agents defined in the YAML config ({@code trading.agents}).
     * YAML-backed agents are created as {@link TechnicalTradingAgent} instances
     * using the symbol and indicator parameters from the config entry.
     */
    public List<TradingAgent> createAgentsFromConfig() {
        List<TradingAgent> agents = new ArrayList<>();
        if (agentProperties.getAgents() == null) return agents;

        for (AgentProperties.AgentConfig config : agentProperties.getAgents()) {
            TradingConfig tradingConfig = new TradingConfig();
            tradingConfig.setSymbol(config.getSymbol());
            String id = config.getSymbol() + "-" + config.getExchange();
            ReactiveTradingAgent agent = buildTechnicalAgent(id, config.getSymbol(),
                    config.getExchange(), tradingConfig);
            agents.add(agent);
        }
        return agents;
    }

    /**
     * Creates a {@link TechnicalTradingAgent} from a DB-backed {@link AgentEntity}.
     * All execution modes route here — gateway selection (live/paper/backtest) is
     * resolved separately by {@link tradingbot.agent.config.OrderExecutionGatewayRegistry}.
     */
    public TradingAgent createAgent(AgentEntity entity) {
        try {
            TradingConfig config = objectMapper.readValue(entity.getGoalDescription(), TradingConfig.class);
            config.setSymbol(entity.getTradingSymbol());
            return buildTechnicalAgent(Ids.asString(entity.getId()), entity.getTradingSymbol(),
                    entity.getExchangeName(), config);
        } catch (Exception e) {
            log.error("Failed to create agent {} — invalid goalDescription JSON: {}",
                    entity.getId(), entity.getGoalDescription(), e);
            throw new RuntimeException("Invalid agent configuration for agent " + entity.getId(), e);
        }
    }

    private ReactiveTradingAgent buildTechnicalAgent(String id, String symbol,
                                                      String exchange, TradingConfig config) {
        return new TechnicalTradingAgent(
                id,
                symbol,
                exchange,
                riskGuard,
                () -> RiskContext.noPosition(id, symbol),
                config.getMacdFastPeriod(),
                config.getMacdSlowPeriod(),
                config.getMacdSignalPeriod(),
                config.getLookbackPeriodRsi(),
                config.getRsiOversoldThreshold(),
                config.getRsiOverboughtThreshold(),
                config.getBbPeriod(),
                config.getBbStandardDeviation());
    }
}
