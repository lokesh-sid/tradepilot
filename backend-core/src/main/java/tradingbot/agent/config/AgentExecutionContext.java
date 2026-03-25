package tradingbot.agent.config;

import org.springframework.stereotype.Component;

import tradingbot.bot.service.FuturesExchangeService;

/**
 * Holds the FuturesExchangeService for the agent currently executing on this thread.
 *
 * LangChain4jStrategy sets the per-agent exchange before calling analyzeAndDecide()
 * and clears it in a finally block. TradingTools and OrderPlacementService call
 * get() to obtain the correct exchange service without needing per-call parameters.
 *
 * ThreadLocal is safe here because AgentOrchestrator executes each agent iteration
 * synchronously on a single thread — there is no async handoff between set() and clear().
 */
@Component
public class AgentExecutionContext {

    private final ThreadLocal<FuturesExchangeService> current = new ThreadLocal<>();
    private final FuturesExchangeService fallback;

    public AgentExecutionContext(FuturesExchangeService fallback) {
        this.fallback = fallback;
    }

    /** Set the exchange service for the agent executing on this thread. */
    public void set(FuturesExchangeService service) {
        current.set(service);
    }

    /**
     * Get the exchange service for the current agent.
     * Falls back to the @Primary bean if no agent context has been set.
     */
    public FuturesExchangeService get() {
        FuturesExchangeService service = current.get();
        return service != null ? service : fallback;
    }

    /** Remove the exchange service binding for this thread. Always call in a finally block. */
    public void clear() {
        current.remove();
    }
}
