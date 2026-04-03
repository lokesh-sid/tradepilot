package tradingbot.agent.application;

import java.time.Instant;

import tradingbot.agent.domain.util.Ids;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import tradingbot.agent.application.event.TradeCompletedEvent;
import tradingbot.agent.domain.execution.ExecutionResult;
import tradingbot.agent.domain.execution.ExecutionResult.ExecutionAction;
import tradingbot.agent.domain.execution.ExecutorRef;
import tradingbot.agent.domain.model.AgentDecision;
import tradingbot.agent.infrastructure.persistence.OrderEntity;
import tradingbot.agent.infrastructure.persistence.TradeJournalEntity.Direction;
import tradingbot.agent.infrastructure.persistence.TradeMemoryEntity;
import tradingbot.bot.metrics.TradingMetrics;

/**
 * TradeExecutionService — single write path for all execution outcomes.
 *
 * <p>Both the kline-driven path ({@link AgentOrchestrator}) and the polling/LLM
 * path ({@link tradingbot.agent.service.OrderPlacementService}) converge here
 * after a gateway call returns an {@link ExecutionResult}. Future bot execution
 * engines call the same method with a bot ID — nothing else changes.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Persist the order via {@link OrderService} — never via repository directly</li>
 *   <li>Update performance metrics via {@link PerformanceTrackingService}</li>
 *   <li>Record Micrometer entry/exit counters</li>
 *   <li>Publish {@link TradeCompletedEvent} for exits (triggers async LLM reflection)</li>
 * </ul>
 *
 * <p>All exceptions are caught and logged — a persistence or metrics failure must
 * never propagate back to the caller and cancel an already-filled position.
 *
 * <p>The {@code executor} parameter is an {@link ExecutorRef} — either an
 * {@link ExecutorRef.AgentRef} (used today) or a {@link ExecutorRef.BotRef}
 * (used by BotOrchestrator in Phase 6). The underlying {@code orders} table
 * stores the executor ID and type via the {@code executor_id}/{@code executor_type}
 * columns added in V11.
 */
@Service
public class TradeExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(TradeExecutionService.class);

    private final OrderService orderService;
    private final PerformanceTrackingService performanceTrackingService;
    private final ApplicationEventPublisher eventPublisher;
    private final TradeJournalService tradeJournalService;
    @Nullable
    private final TradingMetrics tradingMetrics;

    public TradeExecutionService(
            OrderService orderService,
            PerformanceTrackingService performanceTrackingService,
            ApplicationEventPublisher eventPublisher,
            TradeJournalService tradeJournalService,
            @Nullable TradingMetrics tradingMetrics) {
        this.orderService = orderService;
        this.performanceTrackingService = performanceTrackingService;
        this.eventPublisher = eventPublisher;
        this.tradeJournalService = tradeJournalService;
        this.tradingMetrics = tradingMetrics;
    }

    /**
     * Records a completed gateway execution: persists the order, updates performance
     * tracking, records Micrometer counters, and publishes a {@link TradeCompletedEvent}
     * for exits.
     *
     * @param executor the agent or bot that produced the decision
     * @param symbol   trading pair, e.g. {@code BTCUSDT}
     * @param decision the signal that triggered execution
     * @param result   the gateway outcome
     */
    public void record(ExecutorRef executor, String symbol, AgentDecision decision, ExecutionResult result) {
        try {
            String orderId = persistOrder(executor, symbol, decision, result);
            performanceTrackingService.recordExecution(executor.executorId(), result);
            if (result.success() && result.action() != ExecutionAction.NOOP) {
                recordMetrics(symbol, result.action());
                if (isEntry(result.action())) {
                    createJournalEntry(executor.executorId(), symbol, decision, result, orderId);
                } else if (isExit(result.action())) {
                    publishTradeCompleted(executor.executorId(), symbol, decision, result);
                }
            }
        } catch (Exception e) {
            logger.error("[TradeExecutionService] Failed to record execution for {} on {}: {}",
                    executor.executorId(), symbol, e.getMessage(), e);
        }
    }

    private String persistOrder(ExecutorRef executor, String symbol, AgentDecision decision, ExecutionResult result) {
        double quantity = result.fillQuantity() > 0 ? result.fillQuantity()
                : (decision.quantity() != null ? decision.quantity() : 1.0);

        OrderEntity.ExecutorType executorType = executor instanceof ExecutorRef.BotRef
                ? OrderEntity.ExecutorType.BOT
                : OrderEntity.ExecutorType.AGENT;

        OrderEntity.Builder builder = OrderEntity.builder()
                .executorId(Ids.requireId(executor.executorId(), "executorId"))
                .executorType(executorType)
                .symbol(symbol)
                .direction(decision.action() == AgentDecision.Action.BUY
                        ? OrderEntity.Direction.LONG
                        : OrderEntity.Direction.SHORT)
                .price(result.fillPrice())
                .quantity(quantity)
                .createdAt(Instant.now());

        if (result.success() && result.action() != ExecutionAction.NOOP) {
            builder.status(OrderEntity.Status.EXECUTED)
                   .executedAt(Instant.now())
                   .exchangeOrderId(result.exchangeOrderId())
                   .realizedPnl(result.realizedPnl());
        } else {
            builder.status(OrderEntity.Status.FAILED)
                   .failureReason(result.reason());
        }

        return orderService.createOrder(builder.build()).id;
    }

    private void recordMetrics(String symbol, ExecutionAction action) {
        if (tradingMetrics == null) return;
        if (action == ExecutionAction.ENTER_LONG || action == ExecutionAction.ENTER_SHORT) {
            tradingMetrics.recordOrderEntered(symbol,
                    action == ExecutionAction.ENTER_LONG ? "LONG" : "SHORT");
        } else if (action == ExecutionAction.EXIT_LONG || action == ExecutionAction.EXIT_SHORT) {
            tradingMetrics.recordOrderExited(symbol,
                    action == ExecutionAction.EXIT_LONG ? "LONG" : "SHORT");
        }
    }

    private void publishTradeCompleted(String executorId, String symbol,
                                        AgentDecision decision, ExecutionResult result) {
        if (result.action() != ExecutionAction.EXIT_LONG && result.action() != ExecutionAction.EXIT_SHORT) {
            logger.error("[TradeExecutionService] publishTradeCompleted called with non-exit action {} for {} on {} — skipping",
                    result.action(), executorId, symbol);
            return;
        }
        double exitPrice = result.fillPrice();
        double entryPrice = result.entryPrice();
        double realizedPnl = result.realizedPnl();
        boolean isLong = result.action() == ExecutionAction.EXIT_LONG;
        double pnlPercent = entryPrice > 0
                ? (isLong ? exitPrice - entryPrice : entryPrice - exitPrice) / entryPrice * 100.0
                : 0.0;
        TradeMemoryEntity.Direction memDir = isLong
                ? TradeMemoryEntity.Direction.LONG
                : TradeMemoryEntity.Direction.SHORT;
        eventPublisher.publishEvent(new TradeCompletedEvent(
                executorId, symbol, memDir, entryPrice, exitPrice, pnlPercent, realizedPnl, decision.reasoning()));
        logger.info("[TradeExecutionService] TradeCompletedEvent published for {} on {}", executorId, symbol);
    }

    private void createJournalEntry(String executorId, String symbol,
                                     AgentDecision decision, ExecutionResult result,
                                     String orderId) {
        Direction direction = result.action() == ExecutionAction.ENTER_LONG
                ? Direction.LONG : Direction.SHORT;

        double quantity = result.fillQuantity() > 0 ? result.fillQuantity()
                : (decision.quantity() != null ? decision.quantity() : 1.0);

        // For LONG: SL is below entry, TP is above. For SHORT: SL is above entry, TP is below.
        boolean isLong = direction == Direction.LONG;
        Double stopLoss = decision.stopLossPercent() != null
                ? result.fillPrice() * (isLong
                        ? 1.0 - decision.stopLossPercent() / 100.0
                        : 1.0 + decision.stopLossPercent() / 100.0)
                : null;
        Double takeProfit = decision.takeProfitPercent() != null
                ? result.fillPrice() * (isLong
                        ? 1.0 + decision.takeProfitPercent() / 100.0
                        : 1.0 - decision.takeProfitPercent() / 100.0)
                : null;

        tradeJournalService.createEntry(
                executorId, symbol, direction,
                result.fillPrice(), quantity,
                stopLoss, takeProfit,
                decision.confidence(),
                decision.reasoning(),
                orderId,
                result.timestamp());

        logger.debug("[TradeExecutionService] Journal entry created for {} on {}", executorId, symbol);
    }

    private boolean isEntry(ExecutionAction action) {
        return action == ExecutionAction.ENTER_LONG || action == ExecutionAction.ENTER_SHORT;
    }

    private boolean isExit(ExecutionAction action) {
        return action == ExecutionAction.EXIT_LONG || action == ExecutionAction.EXIT_SHORT;
    }
}
