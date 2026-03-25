package tradingbot.agent.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import tradingbot.agent.service.TradeReflectionService;
import tradingbot.agent.service.TradingAgentService;
import tradingbot.agent.service.TradingTools;

/**
 * LangChain4j Configuration for Agentic Trading
 * 
 * This configuration:
 * 1. Sets up the LLM connection (OpenAI-compatible API)
 * 2. Configures chat memory for context retention
 * 3. Wires up the TradingAgentService with tools
 * 
 * The configuration supports any OpenAI-compatible API,
 * including Grok, by setting the base URL and API key.
 */
@Configuration
public class LangChain4jConfig {
    
    @Value("${llm.api-key:dummy}")
    private String apiKey;
    
    @Value("${llm.base-url:https://api.openai.com/v1}")
    private String baseUrl;
    
    @Value("${llm.model:gpt-4}")
    private String modelName;
    
    @Value("${llm.temperature:0.7}")
    private double temperature;
    
    @Value("${llm.timeout:60}")
    private int timeoutSeconds;
    
    @Value("${llm.max-tokens:2000}")
    private int maxTokens;

    /**
     * Maximum number of messages retained in the sliding-window chat memory per agent.
     *
     * <p>Default is 50. Each LangChain4j tool invocation consumes 2 messages (call + result),
     * so a 5-tool analysis cycle needs at least 10 messages just for tooling — the remaining
     * slots carry prior conversation context. Raise this value if agents lose context mid-cycle.
     *
     * <p>Configure via: {@code llm.memory.max-messages=50}
     */
    @Value("${llm.memory.max-messages:50}")
    private int maxMemoryMessages;
    
    /**
     * Create the ChatLanguageModel for LLM interaction
     * 
     * This can be configured to use:
     * - OpenAI (default)
     * - Grok (set base-url to https://api.x.ai/v1)
     * - Any other OpenAI-compatible API
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .temperature(temperature)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .maxTokens(maxTokens)
            .logRequests(true)
            .logResponses(true)
            .build();
    }
    
    /**
     * Create chat memory provider for maintaining per-agent conversation context
     * backed by persistent store.
     */
    @Bean
    public dev.langchain4j.memory.chat.ChatMemoryProvider chatMemoryProvider(dev.langchain4j.store.memory.chat.ChatMemoryStore chatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMemoryMessages)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }
    
    /**
     * Create the TradingAgentService with tools and memory
     * 
     * LangChain4j automatically:
     * - Implements the interface
     * - Wires up tool invocation
     * - Manages conversation memory
     * - Handles retries and errors
     */
    @Bean
    public TradingAgentService tradingAgentService(
            ChatLanguageModel chatLanguageModel,
            dev.langchain4j.memory.chat.ChatMemoryProvider chatMemoryProvider,
            TradingTools tradingTools) {
        
        return AiServices.builder(TradingAgentService.class)
            .chatLanguageModel(chatLanguageModel)
            .chatMemoryProvider(chatMemoryProvider)
            .tools(tradingTools)
            .build();
    }

    /**
     * Create the TradeReflectionService - a lightweight, stateless LLM service
     * used exclusively for post-trade self-reflection.
     *
     * No chat memory or tools are attached — it performs a single focused call
     * to generate a "lesson learned" string from a completed trade's outcome.
     */
    @Bean
    public TradeReflectionService tradeReflectionService(ChatLanguageModel chatLanguageModel) {
        return AiServices.builder(TradeReflectionService.class)
            .chatLanguageModel(chatLanguageModel)
            .build();
    }
}
