package tradingbot.agent.impl.execution;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tradingbot.agent.domain.execution.ExecutionResult;
import tradingbot.agent.domain.execution.ExecutionResult.ExecutionAction;
import tradingbot.agent.domain.execution.OrderExecutionGateway;
import tradingbot.agent.domain.model.AgentDecision;
import tradingbot.agent.domain.model.AgentDecision.Action;
import tradingbot.agent.domain.risk.RiskContext;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.OrderResult;

/**
 * BacktestOrderGateway — routes agent decisions to a
 * {@link tradingbot.bot.service.backtest.BacktestExchangeService} for
 * historical replay.
 *
 * <p>Position state is tracked internally (one directional position per symbol)
 * and the optional {@code riskContextUpdater} callback keeps the
 * {@link RiskContext} in sync so that the {@code RiskGuard} has accurate
 * position data on every candle.
 *
 * <h3>Thread safety</h3>
 * All state is in {@code ConcurrentHashMap}s and critical sections are
 * synchronized on the position-state maps.  The backtest replay loop is
 * single-threaded so this is belt-and-suspenders.
 */
public class BacktestOrderGateway implements OrderExecutionGateway {

    private static final Logger log = LoggerFactory.getLogger(BacktestOrderGateway.class);

    private static final double DEFAULT_QUANTITY = 1.0;

    private final FuturesExchangeService exchange;
    private final Consumer<RiskContext> riskContextUpdater;

    // Keyed by symbol — tracks current position state
    private final Map<String, String> positionSides = new ConcurrentHashMap<>();   // "LONG" | "SHORT"
    private final Map<String, Double> entryPrices   = new ConcurrentHashMap<>();
    private final Map<String, Double> quantities     = new ConcurrentHashMap<>();

    /**
     * @param exchange            the backtest exchange service
     * @param riskContextUpdater  callback invoked after position changes to update
     *                            the agent's {@link RiskContext}; may be {@code null}
     */
    public BacktestOrderGateway(FuturesExchangeService exchange,
                                 Consumer<RiskContext> riskContextUpdater) {
        this.exchange = exchange;
        this.riskContextUpdater = riskContextUpdater;
    }

    @Override
    public ExecutionResult execute(AgentDecision decision, String symbol, double currentPrice) {
        if (decision.action() == Action.HOLD) {
            return ExecutionResult.noop(symbol, "HOLD — no action");
        }

        String side = positionSides.get(symbol);

        if (decision.action() == Action.BUY) {
            return handleBuy(decision, symbol, currentPrice, side);
        } else {
            return handleSell(decision, symbol, currentPrice, side);
        }
    }

    // ── BUY logic ──────────────────────────────────────────────────────────────

    private ExecutionResult handleBuy(AgentDecision decision, String symbol,
                                       double currentPrice, String currentSide) {
        // Already long → noop
        if ("LONG".equals(currentSide)) {
            return ExecutionResult.noop(symbol, "Already LONG — ignoring BUY");
        }

        // Currently short → close short first
        if ("SHORT".equals(currentSide)) {
            return closeShort(decision, symbol, currentPrice);
        }

        // Flat → enter long
        return enterLong(decision, symbol, currentPrice);
    }

    private ExecutionResult enterLong(AgentDecision decision, String symbol, double currentPrice) {
        try {
            OrderResult order = exchange.enterLongPosition(symbol, DEFAULT_QUANTITY);
            double fillPrice = order.getAvgFillPrice() > 0 ? order.getAvgFillPrice() : currentPrice;

            positionSides.put(symbol, "LONG");
            entryPrices.put(symbol, fillPrice);
            quantities.put(symbol, DEFAULT_QUANTITY);

            updateRiskContext(decision.agentId(), symbol);

            log.debug("[BacktestGateway] ENTER_LONG {} @ {} qty={}", symbol, fillPrice, DEFAULT_QUANTITY);
            return ExecutionResult.filled(ExecutionAction.ENTER_LONG, symbol,
                    order.getExchangeOrderId(), fillPrice, DEFAULT_QUANTITY, 0,
                    "Entered LONG via " + decision.reasoning());
        } catch (Exception ex) {
            log.warn("[BacktestGateway] ENTER_LONG failed for {}: {}", symbol, ex.getMessage());
            return ExecutionResult.failed(ExecutionAction.ENTER_LONG, symbol, ex.getMessage());
        }
    }

    // ── SELL logic ─────────────────────────────────────────────────────────────

    private ExecutionResult handleSell(AgentDecision decision, String symbol,
                                        double currentPrice, String currentSide) {
        // Already short → noop
        if ("SHORT".equals(currentSide)) {
            return ExecutionResult.noop(symbol, "Already SHORT — ignoring SELL");
        }

        // Currently long → close long
        if ("LONG".equals(currentSide)) {
            return closeLong(decision, symbol, currentPrice);
        }

        // Flat → enter short
        return enterShort(decision, symbol, currentPrice);
    }

    private ExecutionResult enterShort(AgentDecision decision, String symbol, double currentPrice) {
        try {
            OrderResult order = exchange.enterShortPosition(symbol, DEFAULT_QUANTITY);
            double fillPrice = order.getAvgFillPrice() > 0 ? order.getAvgFillPrice() : currentPrice;

            positionSides.put(symbol, "SHORT");
            entryPrices.put(symbol, fillPrice);
            quantities.put(symbol, DEFAULT_QUANTITY);

            updateRiskContext(decision.agentId(), symbol);

            log.debug("[BacktestGateway] ENTER_SHORT {} @ {} qty={}", symbol, fillPrice, DEFAULT_QUANTITY);
            return ExecutionResult.filled(ExecutionAction.ENTER_SHORT, symbol,
                    order.getExchangeOrderId(), fillPrice, DEFAULT_QUANTITY, 0,
                    "Entered SHORT via " + decision.reasoning());
        } catch (Exception ex) {
            log.warn("[BacktestGateway] ENTER_SHORT failed for {}: {}", symbol, ex.getMessage());
            return ExecutionResult.failed(ExecutionAction.ENTER_SHORT, symbol, ex.getMessage());
        }
    }

    // ── Close helpers ──────────────────────────────────────────────────────────

    private ExecutionResult closeLong(AgentDecision decision, String symbol, double currentPrice) {
        double qty = quantities.getOrDefault(symbol, DEFAULT_QUANTITY);
        double entry = entryPrices.getOrDefault(symbol, currentPrice);
        double balanceBefore = exchange.getMarginBalance();

        try {
            OrderResult order = exchange.exitLongPosition(symbol, qty);
            double fillPrice = order.getAvgFillPrice() > 0 ? order.getAvgFillPrice() : currentPrice;
            double realizedPnl = exchange.getMarginBalance() - balanceBefore;

            clearPosition(symbol);
            updateRiskContext(decision.agentId(), symbol);

            log.debug("[BacktestGateway] EXIT_LONG {} @ {} pnl={}", symbol, fillPrice, realizedPnl);
            return ExecutionResult.filled(ExecutionAction.EXIT_LONG, symbol,
                    order.getExchangeOrderId(), fillPrice, qty, realizedPnl,
                    "Closed LONG via " + decision.reasoning());
        } catch (Exception ex) {
            log.warn("[BacktestGateway] EXIT_LONG failed for {}: {}", symbol, ex.getMessage());
            return ExecutionResult.failed(ExecutionAction.EXIT_LONG, symbol, ex.getMessage());
        }
    }

    private ExecutionResult closeShort(AgentDecision decision, String symbol, double currentPrice) {
        double qty = quantities.getOrDefault(symbol, DEFAULT_QUANTITY);
        double entry = entryPrices.getOrDefault(symbol, currentPrice);
        double balanceBefore = exchange.getMarginBalance();

        try {
            OrderResult order = exchange.exitShortPosition(symbol, qty);
            double fillPrice = order.getAvgFillPrice() > 0 ? order.getAvgFillPrice() : currentPrice;
            double realizedPnl = exchange.getMarginBalance() - balanceBefore;

            clearPosition(symbol);
            updateRiskContext(decision.agentId(), symbol);

            log.debug("[BacktestGateway] EXIT_SHORT {} @ {} pnl={}", symbol, fillPrice, realizedPnl);
            return ExecutionResult.filled(ExecutionAction.EXIT_SHORT, symbol,
                    order.getExchangeOrderId(), fillPrice, qty, realizedPnl,
                    "Closed SHORT via " + decision.reasoning());
        } catch (Exception ex) {
            log.warn("[BacktestGateway] EXIT_SHORT failed for {}: {}", symbol, ex.getMessage());
            return ExecutionResult.failed(ExecutionAction.EXIT_SHORT, symbol, ex.getMessage());
        }
    }

    // ── Position tracking ──────────────────────────────────────────────────────

    private void clearPosition(String symbol) {
        positionSides.remove(symbol);
        entryPrices.remove(symbol);
        quantities.remove(symbol);
    }

    private void updateRiskContext(String agentId, String symbol) {
        if (riskContextUpdater == null) return;

        String side = positionSides.get(symbol);
        if (side == null) {
            riskContextUpdater.accept(RiskContext.noPosition(agentId, symbol));
        } else if ("LONG".equals(side)) {
            riskContextUpdater.accept(RiskContext.longPosition(
                    agentId, symbol,
                    entryPrices.getOrDefault(symbol, 0.0),
                    quantities.getOrDefault(symbol, 0.0),
                    null, null, 2.0, 5.0));  // default SL/TP percentages
        } else {
            riskContextUpdater.accept(RiskContext.shortPosition(
                    agentId, symbol,
                    entryPrices.getOrDefault(symbol, 0.0),
                    quantities.getOrDefault(symbol, 0.0),
                    null, null, 2.0, 5.0));
        }
    }

    // ── Query methods ──────────────────────────────────────────────────────────

    @Override
    public boolean hasOpenPosition(String symbol) {
        return positionSides.containsKey(symbol);
    }

    @Override
    public String getPositionSide(String symbol) {
        return positionSides.get(symbol);
    }

    @Override
    public double getEntryPrice(String symbol) {
        return entryPrices.getOrDefault(symbol, 0.0);
    }

    @Override
    public double getPositionQuantity(String symbol) {
        return quantities.getOrDefault(symbol, 0.0);
    }
}
