package tradingbot.agent.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import tradingbot.agent.ReactiveTradingAgent;
import tradingbot.agent.application.event.AgentPausedEvent;
import tradingbot.agent.application.event.AgentStartedEvent;
import tradingbot.agent.application.event.AgentStoppedEvent;
import tradingbot.agent.application.strategy.AgentStrategy;
import tradingbot.agent.application.strategy.LangChain4jStrategy;
import tradingbot.agent.config.OrderExecutionGatewayRegistry;
import tradingbot.agent.domain.execution.ExecutionResult;
import tradingbot.agent.domain.execution.OrderExecutionGateway;
import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentDecision.Action;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.model.AgentStatus;
import tradingbot.agent.domain.model.AgentSymbolLink;
import tradingbot.agent.domain.repository.AgentRepository;
import tradingbot.agent.infrastructure.persistence.OrderEntity;
import tradingbot.agent.infrastructure.repository.OrderRepository;
import tradingbot.bot.metrics.TradingMetrics;
import tradingbot.domain.market.KlineClosedEvent;
import tradingbot.domain.market.MarketEvent;
import tradingbot.domain.market.StreamMarketDataEvent;
import tradingbot.infrastructure.marketdata.ExchangeWebSocketClient;

/**
 * AgentOrchestrator - Coordinates the agent's sense-think-act loop
 *
 * REFACTORED: Strategy Pattern + Reactive WebSocket Integration
 *
 * Delegates to:
 * - LangChain4jStrategy: Agentic framework with tool use (default and recommended)
 *
 * Configure via: {@code agent.strategy=langchain4j}
 *
 * <p>The {@code rag} and {@code legacy} strategy names are deprecated. They are still
 * accepted at runtime through the deprecated constructor to avoid hard failures, but
 * will be removed in a future release.
 */
@Service
public class AgentOrchestrator {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentOrchestrator.class);

    // -------------------------------------------------------------------------
    // Throttling configuration (externalisable — see application.properties)
    // -------------------------------------------------------------------------

    /**
     * Default minimum interval between consecutive runs for any agent.
     * Override via {@code agent.throttle.default-ms} (default: 5 000 ms).
     */
    @Value("${agent.throttle.default-ms:5000}")
    private long defaultThrottleMs;

    /**
     * Per-symbol throttle overrides.
     * Configured as a SpEL map literal, e.g.:
     * {@code agent.throttle.per-symbol={BTCUSDT:1000, ETHUSDT:3000}}
     * Symbols not listed fall back to {@code defaultThrottleMs}.
     */
    @Value("#{${agent.throttle.per-symbol:{}}}")
    private Map<String, Long> perSymbolThrottleMs;
    
    private final AgentRepository agentRepository;
    private final AgentStrategy activeStrategy;
    private final ExchangeWebSocketClient webSocketClient;
    private final Scheduler agentScheduler;
    
    @Value("${websocket.enabled:false}")
    private boolean websocketEnabled;
    
    // Active WS subscriptions keyed by symbol
    private final Map<String, Disposable> activeSubscriptions = new ConcurrentHashMap<>();
    
    // Cache of active agents interested in each symbol to avoid DB hits on every tick
    // Key: Symbol (e.g. "BTCUSDT"), Value: Set of AgentIds
    private final Map<String, Set<AgentId>> symbolToAgentMap = new ConcurrentHashMap<>();
    
    // Throttling state: AgentId -> Last Execution Time
    private final Map<AgentId, Instant> lastExecutionTime = new ConcurrentHashMap<>();

    // --- Phase 1.5 / Pre-Phase 2: ReactiveTradingAgent event-driven path -------------

    /**
     * Reactive agents implementing {@link ReactiveTradingAgent}.
     * Pre-populated from Spring beans at startup; supplemented at runtime via
     * {@link #registerReactiveAgent} as {@code AgentManager} loads DB-backed agents.
     */
    private final List<ReactiveTradingAgent> agents = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Per-agent bulkhead registry — isolates one misbehaving agent from others.
     * A named {@link Bulkhead} is created lazily on first dispatch per agentId.
     */
    private final BulkheadRegistry bulkheadRegistry;

    /**
     * Unified order execution gateway (P1).  When non-null, agent decisions
     * are routed through this gateway after the signal is produced.
     * Nullable — when absent, decisions are only logged (pre-P1 behaviour).
     */
    private final OrderExecutionGateway executionGateway;
    private final OrderExecutionGatewayRegistry gatewayRegistry;
    private final OrderRepository orderRepository;
    private final PerformanceTrackingService performanceTrackingService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final TradingMetrics tradingMetrics;

    /**
     * Primary constructor — Spring uses this for dependency injection.
     *
     * <p>Only {@link LangChain4jStrategy} is accepted. If {@code agent.strategy}
     * is set to the deprecated values {@code rag} or {@code legacy} a warning is
     * emitted and {@code LangChain4jStrategy} is used as fallback, because the
     * deprecated beans are no longer injected here.
     */
    public AgentOrchestrator(
            AgentRepository agentRepository,
            LangChain4jStrategy langChain4jStrategy,
            ExchangeWebSocketClient webSocketClient,
            List<ReactiveTradingAgent> agents,
            BulkheadRegistry bulkheadRegistry,
            @Nullable OrderExecutionGateway executionGateway,
            OrderExecutionGatewayRegistry gatewayRegistry,
            OrderRepository orderRepository,
            PerformanceTrackingService performanceTrackingService,
            ApplicationEventPublisher eventPublisher,
            TradingMetrics tradingMetrics,
            @Value("${agent.strategy:langchain4j}") String strategyName) {

        this.agentRepository = agentRepository;
        this.webSocketClient = webSocketClient;
        this.agents.addAll(agents);
        this.bulkheadRegistry = bulkheadRegistry;
        this.executionGateway = executionGateway;
        this.gatewayRegistry = gatewayRegistry;
        this.orderRepository = orderRepository;
        this.performanceTrackingService = performanceTrackingService;
        this.eventPublisher = eventPublisher;
        this.tradingMetrics = tradingMetrics;
        this.agentScheduler = Schedulers.boundedElastic();

        String normalized = strategyName.toLowerCase();
        if ("rag".equals(normalized) || "legacy".equals(normalized)) {
            logger.warn("Strategy '{}' is deprecated and its bean is no longer injected into this constructor. "
                    + "Falling back to LangChain4j. Migrate to agent.strategy=langchain4j.", strategyName);
        } else if (!"langchain4j".equals(normalized) && !"agentic".equals(normalized)) {
            logger.warn("Unknown strategy '{}', defaulting to LangChain4j.", strategyName);
        }
        this.activeStrategy = langChain4jStrategy;

        logger.info("╔════════════════════════════════════════════════════════════════╗");
        logger.info("║ AgentOrchestrator initialized with: {}                        ",
                   String.format("%-30s", activeStrategy.getStrategyName()));
        logger.info("╚════════════════════════════════════════════════════════════════╝");
    }

    @PostConstruct
    public void initWebSocket() {
        if (websocketEnabled) {
            logger.info("WebSocket enabled - starting reactive market data streams");
            refreshSubscriptions();
        } else {
            logger.info("WebSocket disabled - using polling mechanism");
        }
    }
    
    /**
     * Refreshes subscriptions based on currently active agents using a
     * lightweight projection query (only id + symbol, no full entity hydration).
     * Used at startup and as a periodic safety-net reconciliation.
     */
    public synchronized void refreshSubscriptions() {
        if (!websocketEnabled) return;
        
        // 1. Use lightweight projection — only ids + symbols, no full entity hydration
        List<AgentSymbolLink> activeLinks = agentRepository.findActiveAgentSymbols();
        
        // 2. Rebuild the symbol -> agent mapping
        symbolToAgentMap.clear();
        for (AgentSymbolLink link : activeLinks) {
            symbolToAgentMap.computeIfAbsent(link.symbol(), k -> ConcurrentHashMap.newKeySet())
                            .add(new AgentId(link.agentId()));
        }
        
        logger.info("[Orchestrator] Refreshed symbolToAgentMap: {} agents across {} symbols",
                activeLinks.size(), symbolToAgentMap.size());
        
        // 3. Ensure we have a subscription for each needed symbol
        symbolToAgentMap.keySet().forEach(symbol -> {
            if (!activeSubscriptions.containsKey(symbol)) {
                logger.info("Subscribing to WebSocket for symbol: {}", symbol);
                Disposable sub = webSocketClient.streamTrades(symbol)
                    .doOnNext(this::handleMarketEvent)
                    .onErrorContinue((ex, obj) -> logger.error("Error in stream for {}: {}", symbol, ex.getMessage()))
                    .subscribe();
                activeSubscriptions.put(symbol, sub);
            }
        });
    }

    /**
     * Reactively handles market events.
     * Finds interested agents and schedules their execution if not throttled.
     */
    private void handleMarketEvent(MarketEvent event) {
        // Find agents interested in this symbol
        // For Phase 1 we hardcode "BTCUSDT" mapping or use event.symbol()
        // If event.symbol() is null, assume global/default but it shouldn't be.
        String symbol = event.symbol() != null ? event.symbol() : "BTCUSDT"; 
        
        Set<AgentId> interestedAgents = symbolToAgentMap.get(symbol);
        
        if (interestedAgents == null || interestedAgents.isEmpty()) {
            return;
        }

        Instant now = Instant.now();

        // Iterate and schedule execution if throttled condition met
        for (AgentId agentId : interestedAgents) {
            // Check throttle
            Instant lastRun = lastExecutionTime.getOrDefault(agentId, Instant.MIN);
            
            if (now.toEpochMilli() - lastRun.toEpochMilli() > getThrottleMs(symbol)) {
                // Update time immediately to prevent double scheduling
                lastExecutionTime.put(agentId, now);
                
                // Offload the blocking/transactional agent logic to a separate scheduler
                // so we don't block the Netty/WebSocket thread.
                agentScheduler.schedule(() -> executeAgentTransactionWrapper(agentId, event));
            }
        }
    }

    /**
     * Non-transactional wrapper to call the transactional method.
     * This ensures the transaction boundary is clean.
     */
    private void executeAgentTransactionWrapper(AgentId agentId, MarketEvent event) {
        try {
            executeAgentTransaction(agentId, event);
        } catch (Exception e) {
            logger.error("Error in async agent execution for {}: {}", agentId, e.getMessage(), e);
        }
    }

    /**
     * Transactional execution of the agent logic.
     * Loads the fresh agent state and runs the strategy.
     */
    @Transactional
    protected void executeAgentTransaction(AgentId agentId, MarketEvent triggeringEvent) {
        // Reload agent to ensure we have fresh state in this transaction
        agentRepository.findById(agentId).ifPresentOrElse(agent -> {
            logger.debug("Executing strategy for agent {} triggered by {} @ {}", 
                agent.getName(), 
                (triggeringEvent instanceof StreamMarketDataEvent se) ? se.type() : "MARKET_EVENT", 
                triggeringEvent.price());
            
            // Execute iteration (Note: this modifies agent state)
            activeStrategy.executeIteration(agent, triggeringEvent);
            
            // Save updated state
            agentRepository.save(agent);
        }, () -> {
            logger.warn("Agent {} not found during execution, evicting from caches", agentId);
            // Remove stale agent from all in-memory caches so it stops receiving events.
            evictAgent(agentId);
        });
    }

    @PreDestroy
    public void cleanup() {
        activeSubscriptions.values().forEach(Disposable::dispose);
        activeSubscriptions.clear();
        agentScheduler.dispose(); // Shutdown scheduler
    }
    
    /**
     * Safety-net reconciliation loop.
     *
     * <p>In WebSocket mode, real-time cache updates are handled by
     * {@link #onAgentStarted}, {@link #onAgentPaused}, and {@link #onAgentStopped}.
     * This loop runs every 5 minutes as a lightweight reconciliation to recover
     * from any missed events (e.g. transaction rollback before event fires).
     *
     * <p>In Polling mode (legacy), this loop runs every 30 seconds and executes
     * each active agent's strategy iteration.
     */
    @Scheduled(fixedDelayString = "${orchestrator.reconciliation.interval-ms:#{${websocket.enabled:false} ? 300000 : 30000}}",
               initialDelay = 10000)
    public void executeAgentLoop() {
        if (websocketEnabled) {
            logger.debug("Reconciliation heartbeat — lightweight projection refresh");
            refreshSubscriptions();
            return;
        }

        logger.debug("Starting agent orchestration cycle (Polling Mode)");
        
        // Polling implementation (Legacy)
        Iterable<Agent> activeAgents = agentRepository.findAllActive();
        int agentCount = 0;
        
        for (Agent agent : activeAgents) {
            try {
                // Throttle check for polling too? 
                // Currently only 30s loop, so native throttling is 30s.
                runSingleAgentIteration(agent);
                agentCount++;
            } catch (Exception e) {
                logger.error("Error in agent loop for agent {}: {}", 
                    agent.getId(), e.getMessage(), e);
            }
        }
        
        if (agentCount > 0) {
            logger.info("Completed cycle for {} agent(s) using {}", 
                agentCount, activeStrategy.getStrategyName());
        }
    }

    // -------------------------------------------------------------------------
    // Phase 1.5 / Pre-Phase 2 — Event-driven path via KlineClosedEvent
    // -------------------------------------------------------------------------

    /**
     * Consumes {@link KlineClosedEvent}s published to
     * {@code kline-closed.{symbol}} Kafka topics by exchange adapters.
     *
     * <p>Design notes (Option C — separate event type):
     * <ul>
     *   <li>This listener only sees fully-closed candles; no TRADE / tick
     *       noise ever reaches agent logic.</li>
     *   <li>A per-agent {@link Bulkhead} is acquired before dispatching so
     *       that one slow LLM call cannot starve other agents.</li>
     *   <li>In backtest profile this method is never invoked because
     *       {@code spring.kafka.listener.auto-startup=false}.</li>
     * </ul>
     *
     * <p>Topic pattern: {@code kline-closed.BTCUSDT}, {@code kline-closed.ETHUSDT}, …
     */
    @KafkaListener(
            topicPattern = "kline-closed\\..*",
            groupId = "agent-orchestrator-klines",
            containerFactory = "kafkaListenerContainerFactory")
    public void onKlineClosedEvent(KlineClosedEvent event) {
        if (agents.isEmpty()) {
            // No ReactiveTradingAgent beans registered yet (normal during Phase 1).
            logger.trace("[KlineListener] No ReactiveTradingAgent beans — skipping dispatch for {}/{}",
                    event.exchange(), event.symbol());
            return;
        }

        agents.stream()
                .filter(agent -> event.symbol().equals(agent.getSymbol()))
                .filter(agent -> agent.getStatus() == AgentStatus.ACTIVE)
                .forEach(agent -> dispatchWithBulkhead(agent, event));
    }

    /**
     * Acquires the per-agent {@link Bulkhead} and dispatches the agent
     * asynchronously on the bounded-elastic scheduler.
     *
     * <p>A bulkhead is created lazily with conservative defaults
     * (10 concurrent calls, 500 ms max wait) if one does not yet exist.
     */
    private void dispatchWithBulkhead(ReactiveTradingAgent agent, KlineClosedEvent event) {
        String bulkheadName = "agent-" + agent.getId();
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(
                bulkheadName,
                () -> BulkheadConfig.custom()
                        .maxConcurrentCalls(10)
                        .maxWaitDuration(Duration.ofMillis(500))
                        .build());

        agentScheduler.schedule(() -> {
            if (!bulkhead.tryAcquirePermission()) {
                logger.warn("[Bulkhead] Agent {} is saturated — dropping event for {}",
                        agent.getId(), event.symbol());
                return;
            }
            try {
                agent.onKlineClosed(event)
                        .doOnSuccess(decision -> {
                            logger.info(
                                    "[AgenticAgent] {} → {} (confidence {}%) for {}",
                                    agent.getId(), decision.action(), decision.confidence(), event.symbol());
                            // P1: Route decision through the per-agent gateway
                            OrderExecutionGateway gateway = gatewayRegistry.resolve(
                                    agent.getId(), agent.getExchange());
                            if (decision.isEntry()) {
                                try {
                                    double price = event.close().doubleValue();
                                    String direction = decision.action() == Action.BUY ? "LONG" : "SHORT";
                                    ExecutionResult result = tradingMetrics != null
                                            ? tradingMetrics.orderPlacementTimer(event.symbol(), direction)
                                                    .recordCallable(() -> gateway.execute(decision, event.symbol(), price))
                                            : gateway.execute(decision, event.symbol(), price);
                                    logger.info("[AgenticAgent] {} execution: {} success={} fill={}",
                                            agent.getId(), result.action(), result.success(), result.fillPrice());

                                    // P2: Persist the order history to the database
                                    try {
                                        double quantity = result.fillQuantity() > 0 ? result.fillQuantity() : 
                                                          (decision.quantity() != null ? decision.quantity() : 1.0);

                                        OrderEntity.Builder entityBuilder = OrderEntity.builder()
                                                .id(UUID.randomUUID().toString())
                                                .agentId(agent.getId())
                                                .symbol(event.symbol())
                                                .direction(decision.action() == Action.BUY ? OrderEntity.Direction.LONG : OrderEntity.Direction.SHORT)
                                                .price(price)
                                                .quantity(quantity)
                                                .createdAt(Instant.now());

                                        if (result.success() && result.action() != ExecutionResult.ExecutionAction.NOOP) {
                                            entityBuilder.status(OrderEntity.Status.EXECUTED)
                                                         .executedAt(Instant.now())
                                                         .exchangeOrderId(result.exchangeOrderId())
                                                         .realizedPnl(result.realizedPnl());
                                            // Record Micrometer entry metric
                                            if (tradingMetrics != null && (
                                                    result.action() == ExecutionResult.ExecutionAction.ENTER_LONG
                                                    || result.action() == ExecutionResult.ExecutionAction.ENTER_SHORT)) {
                                                tradingMetrics.recordOrderEntered(event.symbol(),
                                                        result.action() == ExecutionResult.ExecutionAction.ENTER_LONG ? "LONG" : "SHORT");
                                            }
                                        } else {
                                            entityBuilder.status(OrderEntity.Status.FAILED)
                                                         .failureReason(result.reason());
                                        }
                                        
                                        OrderEntity entity = entityBuilder.build();
                                        orderRepository.save(entity);
                                        
                                        // P3: Track performance metrics
                                        performanceTrackingService.recordExecution(agent.getId(), result);

                                        // P4: Publish async self-reflection event for exit trades
                                        if (result.success() && (
                                                result.action() == ExecutionResult.ExecutionAction.EXIT_LONG ||
                                                result.action() == ExecutionResult.ExecutionAction.EXIT_SHORT)) {
                                            // Record Micrometer exit metric
                                            if (tradingMetrics != null) {
                                                tradingMetrics.recordOrderExited(event.symbol(),
                                                        result.action() == ExecutionResult.ExecutionAction.EXIT_LONG ? "LONG" : "SHORT");
                                            }
                                            double exitPrice = result.fillPrice();
                                            double pnlPercent = result.realizedPnl();
                                            // Approximate entry price: exit / (1 + pnl%)
                                            double impliedEntry = pnlPercent != 0
                                                    ? exitPrice / (1.0 + pnlPercent / 100.0)
                                                    : exitPrice;
                                            tradingbot.agent.infrastructure.persistence.TradeMemoryEntity.Direction memDir =
                                                    result.action() == ExecutionResult.ExecutionAction.EXIT_LONG
                                                    ? tradingbot.agent.infrastructure.persistence.TradeMemoryEntity.Direction.LONG
                                                    : tradingbot.agent.infrastructure.persistence.TradeMemoryEntity.Direction.SHORT;
                                            if (eventPublisher != null) {
                                                eventPublisher.publishEvent(new tradingbot.agent.application.event.TradeCompletedEvent(
                                                        agent.getId(),
                                                        event.symbol(),
                                                        memDir,
                                                        impliedEntry,
                                                        exitPrice,
                                                        pnlPercent,
                                                        decision.reasoning()
                                                ));
                                                logger.info("[P4] TradeCompletedEvent published for agent {} on {}", agent.getId(), event.symbol());
                                            }
                                        }
                                    } catch (Exception dbEx) {
                                        logger.error("[AgenticAgent] {} failed to persist order history or performance: {}", agent.getId(), dbEx.getMessage(), dbEx);
                                    }
                                } catch (Exception exGw) {
                                    logger.error("[AgenticAgent] {} gateway error: {}",
                                            agent.getId(), exGw.getMessage(), exGw);
                                }
                            }
                        })
                        .doOnError(ex -> logger.error(
                                "[AgenticAgent] {} error on kline {}: {}",
                                agent.getId(), event.symbol(), ex.getMessage(), ex))
                        .subscribe();
            } finally {
                bulkhead.releasePermission();
            }
        });
    }

    /**
     * Run a single iteration using the active strategy (Polling version)
     */
    @Transactional
    public void runSingleAgentIteration(Agent agent) {
        logger.info("Running iteration for agent: {} using {} strategy", 
            agent.getName(), activeStrategy.getStrategyName());
        
        try {
            // Polling path — no triggering event available
            activeStrategy.executeIteration(agent, null);
            agentRepository.save(agent);
        } catch (Exception e) {
            logger.error("Failed to complete iteration for agent {}: {}", 
                agent.getId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Returns the throttle interval for a given symbol, falling back to the
     * default if no per-symbol override is configured.
     */
    private long getThrottleMs(String symbol) {
        return perSymbolThrottleMs.getOrDefault(symbol, defaultThrottleMs);
    }

    // -------------------------------------------------------------------------
    // Cache management
    // -------------------------------------------------------------------------

    /**
     * Evicts a stopped or deleted agent from all in-memory caches.
     *
     * <p>Must be called by any service layer method that stops, pauses, or deletes
     * an agent so that stale entries in {@link #symbolToAgentMap} and
     * {@link #lastExecutionTime} do not cause the orchestrator to dispatch
     * market events to a non-running agent.</p>
     *
     * @param agentId the agent to evict
     */
    public synchronized void evictAgent(AgentId agentId) {
        symbolToAgentMap.forEach((symbol, agentIds) -> agentIds.remove(agentId));
        symbolToAgentMap.entrySet().removeIf(e -> e.getValue().isEmpty());
        lastExecutionTime.remove(agentId);
        gatewayRegistry.evict(agentId.getValue());
        logger.info("[Orchestrator] Evicted agent {} from symbolToAgentMap, lastExecutionTime, and gatewayRegistry caches",
                agentId);
    }

    // -------------------------------------------------------------------------
    // Event-driven cache updates
    // -------------------------------------------------------------------------

    /**
     * Reacts to an agent being activated — adds it to the in-memory routing
     * cache and opens a WebSocket subscription for the symbol if needed.
     */
    @EventListener
    public synchronized void onAgentStarted(AgentStartedEvent event) {
        symbolToAgentMap.computeIfAbsent(event.symbol(), k -> ConcurrentHashMap.newKeySet())
                        .add(event.agentId());

        if (websocketEnabled && !activeSubscriptions.containsKey(event.symbol())) {
            logger.info("New symbol detected dynamically, subscribing: {}", event.symbol());
            Disposable sub = webSocketClient.streamTrades(event.symbol())
                .doOnNext(this::handleMarketEvent)
                .onErrorContinue((ex, obj) -> logger.error("Stream error {}: {}", event.symbol(), ex.getMessage()))
                .subscribe();
            activeSubscriptions.put(event.symbol(), sub);
        }
        logger.info("[Orchestrator] Agent {} added to symbolToAgentMap for {}", event.agentId(), event.symbol());
    }

    /**
     * Reacts to an agent being paused — evicts it from routing caches.
     */
    @EventListener
    public void onAgentPaused(AgentPausedEvent event) {
        evictAgent(event.agentId());
    }

    /**
     * Reacts to an agent being stopped or deleted — evicts it from routing caches.
     */
    @EventListener
    public void onAgentStopped(AgentStoppedEvent event) {
        evictAgent(event.agentId());
    }

    // -------------------------------------------------------------------------
    // Dynamic reactive agent registration
    // -------------------------------------------------------------------------

    /**
     * Registers a {@link ReactiveTradingAgent} at runtime.
     * Called by {@code AgentManager} after loading or creating a DB-backed agent.
     */
    public void registerReactiveAgent(ReactiveTradingAgent agent) {
        agents.removeIf(a -> a.getId().equals(agent.getId()));
        agents.add(agent);
        logger.info("[Orchestrator] Registered reactive agent {} for symbol {}", agent.getId(), agent.getSymbol());
    }

    /**
     * Removes a reactive agent from the dispatch list.
     * Called by {@code AgentManager} when an agent is deleted or refreshed.
     */
    public void deregisterReactiveAgent(String agentId) {
        boolean removed = agents.removeIf(a -> a.getId().equals(agentId));
        if (removed) {
            logger.info("[Orchestrator] Deregistered reactive agent {}", agentId);
        }
    }

    // Repository query methods
    public Agent getAgent(AgentId agentId) {
        return agentRepository.findById(agentId)
            .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
    }
    
    public Iterable<Agent> getAllAgents() {
        return agentRepository.findAll();
    }
    
    public Iterable<Agent> getActiveAgents() {
        return agentRepository.findAllActive();
    }
}
