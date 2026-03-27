package tradingbot.agent.domain.execution;

/**
 * ExecutorRef — identifies who placed an order: an agent or a bot.
 *
 * <p>Passed to {@link tradingbot.agent.application.TradeExecutionService#record}
 * so the service can set {@code executor_id} and {@code executor_type} on the
 * persisted order without the caller having to know about the underlying schema.
 *
 * <p>AgentRef is used today by AgentOrchestrator and OrderPlacementService.
 * BotRef will be used by BotOrchestrator once Phase 6 is implemented.
 */
public sealed interface ExecutorRef permits ExecutorRef.AgentRef, ExecutorRef.BotRef {

    String executorId();

    record AgentRef(String executorId) implements ExecutorRef {}

    record BotRef(String executorId, String orderGroupId) implements ExecutorRef {}
}
