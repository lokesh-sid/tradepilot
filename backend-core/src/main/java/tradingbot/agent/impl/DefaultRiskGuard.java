package tradingbot.agent.impl;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tradingbot.agent.domain.model.AgentDecision;
import tradingbot.agent.domain.model.AgentDecision.Action;
import tradingbot.agent.domain.risk.RiskContext;
import tradingbot.agent.domain.risk.RiskGuard;
import tradingbot.domain.market.KlineClosedEvent;

/**
 * DefaultRiskGuard — synchronous, sub-millisecond risk evaluation that
 * runs <em>before</em> any LLM call.
 *
 * <h3>Checks performed (in order)</h3>
 * <ol>
 *   <li><b>No position</b>: if there is no open position, return empty
 *       (nothing to protect).</li>
 *   <li><b>Hard stop-loss price</b>: if the candle's close &le; the
 *       configured stop-loss price (for longs), exit immediately.</li>
 *   <li><b>Hard take-profit price</b>: if the candle's close &ge; the
 *       configured take-profit price (for longs), exit to lock in gains.</li>
 *   <li><b>Percentage-based stop-loss</b>: if unrealised loss exceeds
 *       {@code maxLossPercent}, exit.</li>
 *   <li><b>Percentage-based take-profit</b>: if unrealised gain exceeds
 *       {@code maxGainPercent}, exit.</li>
 * </ol>
 *
 * <p>Short positions mirror the logic (price direction inverted).
 *
 * <p>All checks are pure arithmetic — no I/O, no blocking calls,
 * no database reads.  This guarantees bounded latency (&lt; 1 ms).
 */
@Component
public class DefaultRiskGuard implements RiskGuard {

    private static final Logger log = LoggerFactory.getLogger(DefaultRiskGuard.class);

    @Override
    public Optional<AgentDecision> evaluate(KlineClosedEvent event, RiskContext ctx) {
        if (!ctx.hasOpenPosition()) {
            return Optional.empty(); // nothing to protect
        }

        double currentPrice = event.close().doubleValue();
        double entryPrice   = ctx.entryPrice();
        boolean isLong      = "LONG".equalsIgnoreCase(ctx.positionSide());

        // ── 1. Hard stop-loss price ────────────────────────────────────────
        if (ctx.stopLossPrice() != null) {
            boolean stopHit = isLong
                    ? currentPrice <= ctx.stopLossPrice()
                    : currentPrice >= ctx.stopLossPrice();
            if (stopHit) {
                String reason = "RISK_GUARD: Hard stop-loss triggered at %.4f (SL=%.4f, entry=%.4f, side=%s)"
                        .formatted(currentPrice, ctx.stopLossPrice(), entryPrice, ctx.positionSide());
                log.warn("[{}] {}", ctx.agentId(), reason);
                return Optional.of(exitDecision(ctx, 100, reason));
            }
        }

        // ── 2. Hard take-profit price ──────────────────────────────────────
        if (ctx.takeProfitPrice() != null) {
            boolean tpHit = isLong
                    ? currentPrice >= ctx.takeProfitPrice()
                    : currentPrice <= ctx.takeProfitPrice();
            if (tpHit) {
                String reason = "RISK_GUARD: Hard take-profit triggered at %.4f (TP=%.4f, entry=%.4f, side=%s)"
                        .formatted(currentPrice, ctx.takeProfitPrice(), entryPrice, ctx.positionSide());
                log.info("[{}] {}", ctx.agentId(), reason);
                return Optional.of(exitDecision(ctx, 100, reason));
            }
        }

        // ── 3. Percentage-based stop-loss ──────────────────────────────────
        if (entryPrice > 0) {
            double pnlPercent = isLong
                    ? ((currentPrice - entryPrice) / entryPrice) * 100.0
                    : ((entryPrice - currentPrice) / entryPrice) * 100.0;

            if (ctx.maxLossPercent() > 0 && pnlPercent <= -ctx.maxLossPercent()) {
                String reason = "RISK_GUARD: Max-loss %.2f%% breached (P&L=%.2f%%, entry=%.4f, current=%.4f, side=%s)"
                        .formatted(ctx.maxLossPercent(), pnlPercent, entryPrice, currentPrice, ctx.positionSide());
                log.warn("[{}] {}", ctx.agentId(), reason);
                return Optional.of(exitDecision(ctx, 100, reason));
            }

            // ── 4. Percentage-based take-profit ────────────────────────────
            if (ctx.maxGainPercent() > 0 && pnlPercent >= ctx.maxGainPercent()) {
                String reason = "RISK_GUARD: Max-gain %.2f%% reached (P&L=%.2f%%, entry=%.4f, current=%.4f, side=%s)"
                        .formatted(ctx.maxGainPercent(), pnlPercent, entryPrice, currentPrice, ctx.positionSide());
                log.info("[{}] {}", ctx.agentId(), reason);
                return Optional.of(exitDecision(ctx, 100, reason));
            }
        }

        return Optional.empty(); // no risk override — proceed to LLM
    }

    /**
     * Builds an immediate SELL decision for the agent.
     * For short positions this still means "exit" — the downstream gateway
     * interprets SELL as "close the current position".
     */
    private AgentDecision exitDecision(RiskContext ctx, int confidence, String reason) {
        // Pass through current position's quantity, maxLossPercent, and maxGainPercent as SL/TP percent
        Double quantity = ctx.quantity() > 0 ? ctx.quantity() : null;
        Double stopLossPercent = ctx.maxLossPercent() > 0 ? ctx.maxLossPercent() : null;
        Double takeProfitPercent = ctx.maxGainPercent() > 0 ? ctx.maxGainPercent() : null;
        return AgentDecision.of(ctx.agentId(), ctx.symbol(), Action.SELL, confidence, reason, quantity, stopLossPercent, takeProfitPercent);
    }
}
