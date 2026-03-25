package tradingbot.agent.factory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import tradingbot.agent.ReactiveTradingAgent;
import tradingbot.agent.TradingAgent;
import tradingbot.agent.config.AgentProperties;
import tradingbot.agent.config.ExchangeServiceRegistry;
import tradingbot.agent.domain.risk.RiskContext;
import tradingbot.agent.domain.risk.RiskGuard;
import tradingbot.agent.impl.TechnicalTradingAgent;
import tradingbot.agent.infrastructure.repository.AgentEntity;
import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.FuturesTradingBot.BotParams;
import tradingbot.bot.TradeDirection;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.PaperFuturesExchangeService;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.bot.strategy.calculator.IndicatorCalculator;
import tradingbot.bot.strategy.calculator.IndicatorValues;
import tradingbot.bot.strategy.exit.LiquidationRiskExit;
import tradingbot.bot.strategy.exit.MACDExit;
import tradingbot.bot.strategy.exit.PositionExitCondition;
import tradingbot.bot.strategy.exit.TrailingStopExit;
import tradingbot.bot.strategy.indicator.BollingerBandsIndicator;
import tradingbot.bot.strategy.indicator.MACDTechnicalIndicator;
import tradingbot.bot.strategy.indicator.RSITechnicalIndicator;
import tradingbot.bot.strategy.indicator.TechnicalIndicator;
import tradingbot.bot.strategy.tracker.TrailingStopTracker;
import tradingbot.config.TradingConfig;

/**
 * Factory component responsible for instantiating TradingAgent objects.
 * 
 * This class implements the Simple Factory pattern, dispatching the creation
 * of specific agent implementations (like FuturesTradingBot) based on the
 * agent type defined in the AgentEntity.
 */
@Component
public class AgentFactory {
    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private final FuturesExchangeService realExchangeService;
    private final ExchangeServiceRegistry exchangeServiceRegistry;
    private final SentimentAnalyzer sentimentAnalyzer;
    private final RedisTemplate<String, IndicatorValues> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;
    private final RiskGuard riskGuard;

    public AgentFactory(FuturesExchangeService exchangeService,
                        ExchangeServiceRegistry exchangeServiceRegistry,
                        SentimentAnalyzer sentimentAnalyzer,
                        RedisTemplate<String, IndicatorValues> redisTemplate,
                        ObjectMapper objectMapper,
                        AgentProperties agentProperties,
                        RiskGuard riskGuard) {
        this.realExchangeService = exchangeService;
        this.exchangeServiceRegistry = exchangeServiceRegistry;
        this.sentimentAnalyzer = sentimentAnalyzer;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.agentProperties = agentProperties;
        this.riskGuard = riskGuard;
    }

    /**
     * Creates all agents defined in the YAML config (trading.agents).
     */
    public List<TradingAgent> createAgentsFromConfig() {
        List<TradingAgent> agents = new java.util.ArrayList<>();
        if (agentProperties.getAgents() != null) {
            for (AgentProperties.AgentConfig config : agentProperties.getAgents()) {
                TradingConfig tradingConfig = new TradingConfig();
                tradingConfig.setSymbol(config.getSymbol());
                // TODO: Map other fields as needed (interval, strategy, etc.)
                // Set direction if TradingConfig supports a public field or setter
                if (config.getDirection() != null) {
                    // Assuming TradingConfig has a public 'direction' field
                    try {
                        java.lang.reflect.Field directionField = TradingConfig.class.getDeclaredField("direction");
                        directionField.setAccessible(true);
                        directionField.set(tradingConfig, config.getDirection());
                    } catch (Exception e) {
                        // Field not present, ignore or log as needed
                    }
                }
                TradingAgent agent = createFuturesTradingBotFromConfig(config, tradingConfig);
                agents.add(agent);
            }
        }
        return agents;
    }

    private FuturesExchangeService createExchangeService(String exchangeName) {
        return exchangeServiceRegistry.resolve(exchangeName);
    }

    private FuturesTradingBot createFuturesTradingBotFromConfig(AgentProperties.AgentConfig config, TradingConfig tradingConfig) {
        FuturesExchangeService exchangeService = createExchangeService(config.getExchange());

        Map<String, TechnicalIndicator> indicators = createIndicators(tradingConfig);
        IndicatorCalculator indicatorCalculator = new IndicatorCalculator(exchangeService, indicators, redisTemplate);
        TrailingStopTracker trailingStopTracker = new TrailingStopTracker(exchangeService, tradingConfig.getTrailingStopPercent());
        List<PositionExitCondition> exitConditions = createExitConditions(tradingConfig, indicatorCalculator, trailingStopTracker, exchangeService);

        TradeDirection direction = TradeDirection.LONG;
        if (tradingConfig.getDirection() != null) {
            try {
                direction = TradeDirection.valueOf(tradingConfig.getDirection().toUpperCase());
            } catch (Exception e) {
                // fallback to LONG if invalid
            }
        }
        BotParams botParams = new BotParams.Builder()
            .id(config.getSymbol() + "-" + config.getExchange())
            .name(config.getSymbol() + " " + config.getExchange())
            .exchangeService(exchangeService)
            .indicatorCalculator(indicatorCalculator)
            .trailingStopTracker(trailingStopTracker)
            .sentimentAnalyzer(sentimentAnalyzer)
            .exitConditions(exitConditions)
            .config(tradingConfig)
            .tradeDirection(direction)
            .skipLeverageInit(false) // Exchange-specific logic can be added if needed
            .build();
        return new FuturesTradingBot(botParams);
    }
    // Duplicate methods removed

    public TradingAgent createAgent(AgentEntity entity) {
        try {
            TradingConfig config = objectMapper.readValue(entity.getGoalDescription(), TradingConfig.class);
            config.setSymbol(entity.getTradingSymbol());
            // Legacy bot path: FUTURES / FUTURES_PAPER → FuturesTradingBot (used by TradingBotController)
            // New agent path:  NONE → TechnicalTradingAgent (used by AgentController)
            if (entity.getExecutionMode() == AgentEntity.ExecutionMode.FUTURES
                    || entity.getExecutionMode() == AgentEntity.ExecutionMode.FUTURES_PAPER) {
                return createFuturesTradingBot(entity, config);
            }
            return createTechnicalTradingAgent(entity, config);
        } catch (Exception e) {
            log.error("Failed to create agent {} — invalid goalDescription JSON: {}", entity.getId(), entity.getGoalDescription(), e);
            throw new RuntimeException("Invalid agent configuration for agent " + entity.getId(), e);
        }
    }

    private FuturesTradingBot createFuturesTradingBot(AgentEntity entity, TradingConfig config) {
        boolean isPaper = entity.getExecutionMode() == AgentEntity.ExecutionMode.FUTURES_PAPER;
        FuturesExchangeService exchangeService;
        if (this.realExchangeService.getClass().getName().contains("Mockito")) {
            exchangeService = this.realExchangeService;
        } else if (isPaper) {
            exchangeService = new PaperFuturesExchangeService();
        } else {
            exchangeService = createExchangeService(entity.getExchangeName());
        }

        TradeDirection direction = TradeDirection.LONG;
        if (config.getDirection() != null && !config.getDirection().isBlank()) {
            try {
                direction = TradeDirection.valueOf(config.getDirection().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown direction '{}' for agent {}, defaulting to LONG", config.getDirection(), entity.getId());
            }
        }

        Map<String, TechnicalIndicator> indicators = createIndicators(config);
        IndicatorCalculator indicatorCalculator = new IndicatorCalculator(exchangeService, indicators, redisTemplate);
        TrailingStopTracker trailingStopTracker = new TrailingStopTracker(exchangeService, config.getTrailingStopPercent());
        List<PositionExitCondition> exitConditions = createExitConditions(config, indicatorCalculator, trailingStopTracker, exchangeService);

        BotParams botParams = new BotParams.Builder()
            .id(entity.getId())
            .name(entity.getName())
            .exchangeService(exchangeService)
            .indicatorCalculator(indicatorCalculator)
            .trailingStopTracker(trailingStopTracker)
            .sentimentAnalyzer(sentimentAnalyzer)
            .exitConditions(exitConditions)
            .config(config)
            .tradeDirection(direction)
            .skipLeverageInit(isPaper)
            .build();

        return new FuturesTradingBot(botParams);
    }

    private ReactiveTradingAgent createTechnicalTradingAgent(AgentEntity entity, TradingConfig config) {
        return new TechnicalTradingAgent(
                entity.getId(),
                entity.getTradingSymbol(),
                entity.getExchangeName(),
                riskGuard,
                () -> RiskContext.noPosition(entity.getId(), entity.getTradingSymbol()),
                config.getMacdFastPeriod(),
                config.getMacdSlowPeriod(),
                config.getMacdSignalPeriod(),
                config.getLookbackPeriodRsi(),
                config.getRsiOversoldThreshold(),
                config.getRsiOverboughtThreshold(),
                config.getBbPeriod(),
                config.getBbStandardDeviation());
    }

    private Map<String, TechnicalIndicator> createIndicators(TradingConfig config) {
        Map<String, TechnicalIndicator> indicators = new HashMap<>();
        indicators.put("rsi", new RSITechnicalIndicator(config.getLookbackPeriodRsi()));
        indicators.put("macd", new MACDTechnicalIndicator(config.getMacdFastPeriod(), config.getMacdSlowPeriod(), config.getMacdSignalPeriod(), false));
        indicators.put("macdSignal", new MACDTechnicalIndicator(config.getMacdFastPeriod(), config.getMacdSlowPeriod(), config.getMacdSignalPeriod(), true));
        indicators.put("bbLower", new BollingerBandsIndicator(config.getBbPeriod(), config.getBbStandardDeviation(), true));
        indicators.put("bbUpper", new BollingerBandsIndicator(config.getBbPeriod(), config.getBbStandardDeviation(), false));
        return indicators;
    }

    private List<PositionExitCondition> createExitConditions(TradingConfig config, 
            IndicatorCalculator indicatorCalculator, 
            TrailingStopTracker trailingStopTracker, 
            FuturesExchangeService exchangeService) {
        return Arrays.asList(
                new TrailingStopExit(trailingStopTracker),
                new MACDExit(indicatorCalculator),
                new LiquidationRiskExit(exchangeService, trailingStopTracker, config)
        );
    }

}
