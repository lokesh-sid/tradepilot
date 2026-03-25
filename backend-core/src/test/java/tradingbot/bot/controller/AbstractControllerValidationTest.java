package tradingbot.bot.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import tradingbot.AbstractHttpTest;
import tradingbot.agent.TradingAgentFactory;
import tradingbot.agent.api.dto.AgentMapper;
import tradingbot.agent.application.AgentService;
import tradingbot.agent.application.PerformanceTrackingService;
import tradingbot.config.TradingSafetyService;
import tradingbot.agent.infrastructure.llm.LLMProvider;
import tradingbot.agent.infrastructure.repository.AgentPerformanceRepository;
import tradingbot.agent.infrastructure.repository.ChatMessageRepository;
import tradingbot.agent.infrastructure.repository.DeadLetterRepository;
import tradingbot.agent.infrastructure.repository.JpaAgentRepository;
import tradingbot.agent.infrastructure.repository.OrderRepository;
import tradingbot.agent.infrastructure.repository.PositionRepository;
import tradingbot.agent.infrastructure.repository.TradeMemoryRepository;
import tradingbot.agent.manager.AgentManager;
import tradingbot.bot.FuturesTradingBot;
import tradingbot.bot.messaging.EventPublisher;
import tradingbot.bot.persistence.repository.TradingEventRepository;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.strategy.analyzer.SentimentAnalyzer;
import tradingbot.config.InstanceConfig;
import tradingbot.security.repository.UserRepository;

/**
 * Abstract base class for controller validation tests.
 * Provides common Spring Boot test setup and validation test utilities.
 */
@SpringBootTest(
    classes = tradingbot.bot.controller.config.TradingBotControllerValidationTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc(addFilters = false)
public abstract class AbstractControllerValidationTest extends AbstractHttpTest {

    @MockitoBean
    protected AgentManager agentManager;


    @MockitoBean
    protected JpaAgentRepository jpaAgentRepository;

    @MockitoBean
    protected OrderRepository orderRepository;

    @MockitoBean
    protected PositionRepository positionRepository;

    @MockitoBean
    protected AgentPerformanceRepository agentPerformanceRepository;

    @MockitoBean
    protected DeadLetterRepository deadLetterRepository;

    @MockitoBean
    protected TradeMemoryRepository tradeMemoryRepository;

    @MockitoBean
    protected ChatMessageRepository chatMessageRepository;

    @MockitoBean
    protected RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    protected LLMProvider llmProvider;

    @MockitoBean
    protected TradingEventRepository tradingEventRepository;

    @MockitoBean
    protected UserRepository userRepository;

    @MockitoBean
    protected FuturesTradingBot tradingBot;

    @MockitoBean
    protected EventPublisher eventPublisher;

    @MockitoBean
    protected InstanceConfig instanceConfig;

    @MockitoBean
    protected FuturesExchangeService exchangeService;

    @MockitoBean
    protected SentimentAnalyzer sentimentAnalyzer;

    @MockitoBean
    protected AgentService agentService;

    @MockitoBean
    protected AgentMapper agentMapper;

    @MockitoBean
    protected PerformanceTrackingService performanceTrackingService;

    @MockitoBean
    protected TradingSafetyService tradingSafetyService;

    @MockitoBean
    protected TradingAgentFactory tradingAgentFactory;

    /**
     * Performs a POST request and expects validation failure with field errors.
     */
    protected ResultActions performValidationTest(String url, Object requestBody, String expectedField, String expectedMessage) throws Exception {
        return performPost(url, requestBody)
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.title").value("Validation Failed"))
               .andExpect(jsonPath("$.fieldErrors." + expectedField + "[0]").value(expectedMessage));
    }

    /**
     * Performs a request and expects it to pass validation (not return 400 with validation errors).
     */
    protected ResultActions performValidRequestTest(String url, Object requestBody) throws Exception {
        return performPost(url, requestBody)
               .andExpect(result -> {
                   int status = result.getResponse().getStatus();
                   if (status == 400) {
                       String body = result.getResponse().getContentAsString();
                       if (body.contains("Validation Failed") || body.contains("Constraint Violation")) {
                           throw new AssertionError("Valid request should not fail validation");
                       }
                   }
               });
    }
}