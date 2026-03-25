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
 * PaperTradingOrderGateway — routes agent decisions to a
 * {@link tradingbot.bot.service.PaperFuturesExchangeService} for
 * simulated live trading.
 *
 * <p>Functionally identical to {@link BacktestOrderGateway} but intended for
 * the paper-trading profile where real market data feeds in but fills are
 * simulated.  Separated into its own class for profile-specific Spring wiring
 * and future divergence (e.g. simulated latency, partial fills).
 *
 * <h3>Position model</h3>
 * One directional position per symbol.  No pyramiding.
 */
public class PaperTradingOrderGateway implements OrderExecutionGateway {

    private static final Logger log = LoggerFactory.getLogger(PaperTradingOrderGateway.class);

    private static final double DEFAULT_QUANTITY = 1.0;

    private final FuturesExchangeService exchange;
    private final Consumer<RiskContext> riskContextUpdater;

    private final Map<String, String> positionSides = new ConcurrentHashMap<>();
    private final Map<String, Double> entryPrices   = new ConcurrentHashMap<>();
    private final Map<String, Double> quantities     = new ConcurrentHashMap<>();

    /**
     * @param exchange            the paper exchange service
     * @param riskContextUpdater  callback for RiskContext updates; may be {@code null}
     */
    public PaperTradingOrderGateway(FuturesExchangeService exchange,
                                     Consumer<RiskContext> riskContextUpdater) {
        this.exchange = exchange;
        this.riskContextUpdater = riskContextUpdater;
    }

    @Override
    public ExecutionResult execute(AgentDecision decision, String symbol, double currentPrice) {
        if (decision.action() == Action.HOLD) {
            return ExecutionResult.noop(symbol, "HOLD — no action");
        }

        log.info("[PaperGateway] Executing {} for {} @ {} (agent={})",
                decision.action(), symbol, currentPrice, decision.agentId());

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
        if ("LONG".equals(currentSide)) {
            return ExecutionResult.noop(symbol, "Already LONG — ignoring BUY");
        }
        if ("SHORT".equals(currentSide)) {
            return closeShort(decision, symbol, currentPrice);
        }
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

            log.info("[PaperGateway] ENTER_LONG {} @ {} qty={}", symbol, fillPrice, DEFAULT_QUANTITY);
            return ExecutionResult.filled(ExecutionAction.ENTER_LONG, symbol,
                    order.getExchangeOrderId(), fillPrice, DEFAULT_QUANTITY, 0,
                    "Paper LONG entry");
        } catch (Exception ex) {
            log.warn("[PaperGateway] ENTER_LONG failed for {}: {}", symbol, ex.getMessage());
            return ExecutionResult.failed(ExecutionAction.ENTER_LONG, symbol, ex.getMessage());
        }
    }

    // ── SELL logic ─────────────────────────────────────────────────────────────

    private ExecutionResult handleSell(AgentDecision decision, String symbol,
                                        double currentPrice, String currentSide) {
        if ("SHORT".equals(currentSide)) {
            return ExecutionResult.noop(symbol, "Already SHORT — ignoring SELL");
        }
        if ("LONG".equals(currentSide)) {
            return closeLong(decision, symbol, currentPrice);
        }
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

            log.info("[PaperGateway] ENTER_SHORT {} @ {} qty={}", symbol, fillPrice, DEFAULT_QUANTITY);
            return ExecutionResult.filled(ExecutionAction.ENTER_SHORT, symbol,
                    order.getExchangeOrderId(), fillPrice, DEFAULT_QUANTITY, 0,
                    "Paper SHORT entry");
        } catch (Exception ex) {
            log.warn("[PaperGateway] ENTER_SHORT failed for {}: {}", symbol, ex.getMessage());
            return ExecutionResult.failed(ExecutionAction.ENTER_SHORT, symbol, ex.getMessage());
        }
    }

    // ── Close helpers ──────────────────────────────────────────────────────────

    private ExecutionResult closeLong(AgentDecision decision, String symbol, double currentPrice) {
        double qty = quantities.getOrDefault(symbol, DEFAULT_QUANTITY);
        double balanceBefore = exchange.getMarginBalance();

        try {
            OrderResult order = exchange.exitLongPosition(symbol, qty);
            double fillPrice = order.getAvgFillPrice() > 0 ? order.getAvgFillPrice() : currentPrice;
            double realizedPnl = exchange.getMarginBalance() - balanceBefore;

            clearPosition(symbol);
            updateRiskContext(decision.agentId(), symbol);

            log.info("[PaperGateway] EXIT_LONG {} @ {} pnl={}", symbol, fillPrice, realizedPnl);
            return ExecutionResult.filled(ExecutionAction.EXIT_LONG, symbol,
                    order.getExchangeOrderId(), fillPrice, qty, realizedPnl,
                    "Paper LONG exit");
        } catch (Exception ex) {
            log.warn("[PaperGateway] EXIT_LONG failed for {}: {}", symbol, ex.getMessage());
            return ExecutionResult.failed(ExecutionAction.EXIT_LONG, symbol, ex.getMessage());
        }
    }

    private ExecutionResult closeShort(AgentDecision decision, String symbol, double currentPrice) {
        double qty = quantities.getOrDefault(symbol, DEFAULT_QUANTITY);
        double balanceBefore = exchange.getMarginBalance();

        try {
            OrderResult order = exchange.exitShortPosition(symbol, qty);
            double fillPrice = order.getAvgFillPrice() > 0 ? order.getAvgFillPrice() : currentPrice;
            double realizedPnl = exchange.getMarginBalance() - balanceBefore;

            clearPosition(symbol);
            updateRiskContext(decision.agentId(), symbol);

            log.info("[PaperGateway] EXIT_SHORT {} @ {} pnl={}", symbol, fillPrice, realizedPnl);
            return ExecutionResult.filled(ExecutionAction.EXIT_SHORT, symbol,
                    order.getExchangeOrderId(), fillPrice, qty, realizedPnl,
                    "Paper SHORT exit");
        } catch (Exception ex) {
            log.warn("[PaperGateway] EXIT_SHORT failed for {}: {}", symbol, ex.getMessage());
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
                    null, null, 2.0, 5.0));
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
