package tradingbot.agent.impl;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import tradingbot.agent.domain.model.AgentDecision;
import tradingbot.agent.domain.model.AgentDecision.Action;
import tradingbot.agent.domain.risk.RiskContext;
import tradingbot.domain.market.KlineClosedEvent;

/**
 * Unit tests for {@link DefaultRiskGuard}.
 *
 * <p>All tests are pure, in-memory, deterministic — no Spring context, no I/O.
 */
class DefaultRiskGuardTest {

    private DefaultRiskGuard guard;

    @BeforeEach
    void setUp() {
        guard = new DefaultRiskGuard();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private KlineClosedEvent candleAt(double closePrice) {
        return new KlineClosedEvent(
                "TEST", "BTCUSDT", "1m",
                BigDecimal.valueOf(closePrice),  // open
                BigDecimal.valueOf(closePrice),  // high
                BigDecimal.valueOf(closePrice),  // low
                BigDecimal.valueOf(closePrice),  // close
                BigDecimal.valueOf(1000),         // volume
                Instant.now().minusSeconds(60),
                Instant.now());
    }

    // ── No position ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("No position → empty (nothing to protect)")
    void noPosition_returnsEmpty() {
        RiskContext ctx = RiskContext.noPosition("agent-1", "BTCUSDT");
        Optional<AgentDecision> result = guard.evaluate(candleAt(50_000), ctx);
        assertThat(result).isEmpty();
    }

    // ── Hard stop-loss (LONG) ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Hard stop-loss — LONG position")
    class HardStopLossLong {

        @Test
        @DisplayName("Price at stop-loss → triggers exit")
        void atStopLoss_triggers() {
            RiskContext ctx = RiskContext.longPosition("a1", "BTCUSDT",
                    50_000, 1.0, 48_000.0, null, 0, 0);
            Optional<AgentDecision> result = guard.evaluate(candleAt(48_000), ctx);
            assertThat(result).isPresent();
            assertThat(result.get().action()).isEqualTo(Action.SELL);
            assertThat(result.get().confidence()).isEqualTo(100);
            assertThat(result.get().reasoning()).contains("stop-loss");
        }

        @Test
        @DisplayName("Price below stop-loss → triggers exit")
        void belowStopLoss_triggers() {
            RiskContext ctx = RiskContext.longPosition("a1", "BTCUSDT",
                    50_000, 1.0, 48_000.0, null, 0, 0);
            Optional<AgentDecision> result = guard.evaluate(candleAt(47_500), ctx);
            assertThat(result).isPresent();
            assertThat(result.get().action()).isEqualTo(Action.SELL);
        }

        @Test
        @DisplayName("Price above stop-loss → no trigger")
        void aboveStopLoss_noTrigger() {
            RiskContext ctx = RiskContext.longPosition("a1", "BTCUSDT",
                    50_000, 1.0, 48_000.0, null, 0, 0);
            Optional<AgentDecision> result = guard.evaluate(candleAt(49_000), ctx);
            assertThat(result).isEmpty();
        }
    }

    // ── Hard take-profit (LONG) ────────────────────────────────────────────────

    @Nested
    @DisplayName("Hard take-profit — LONG position")
    class HardTakeProfitLong {

        @Test
        @DisplayName("Price at take-profit → triggers exit")
        void atTakeProfit_triggers() {
            RiskContext ctx = RiskContext.longPosition("a1", "BTCUSDT",
                    50_000, 1.0, null, 55_000.0, 0, 0);
            Optional<AgentDecision> result = guard.evaluate(candleAt(55_000), ctx);
            assertThat(result).isPresent();
            assertThat(result.get().action()).isEqualTo(Action.SELL);
            assertThat(result.get().reasoning()).contains("take-profit");
        }

        @Test
        @DisplayName("Price below take-profit → no trigger")
        void belowTakeProfit_noTrigger() {
            RiskContext ctx = RiskContext.longPosition("a1", "BTCUSDT",
                    50_000, 1.0, null, 55_000.0, 0, 0);
            Optional<AgentDecision> result = guard.evaluate(candleAt(54_000), ctx);
            assertThat(result).isEmpty();
        }
    }

    // ── Hard stop-loss (SHORT) ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Hard stop-loss — SHORT position")
    class HardStopLossShort {

        @Test
        @DisplayName("Price at stop-loss (above entry) → triggers exit")
        void atStopLoss_triggers() {
            RiskContext ctx = RiskContext.shortPosition("a1", "BTCUSDT",
                    50_000, 1.0, 52_000.0, null, 0, 0);
            Optional<AgentDecision> result = guard.evaluate(candleAt(52_000), ctx);
            assertThat(result).isPresent();
            assertThat(result.get().action()).isEqualTo(Action.SELL);
        }

        @Test
        @DisplayName("Price below stop-loss → no trigger")
        void belowStopLoss_noTrigger() {
            RiskContext ctx = RiskContext.shortPosition("a1", "BTCUSDT",
                    50_000, 1.0, 52_000.0, null, 0, 0);
            Optional<AgentDecision> result = guard.evaluate(candleAt(49_000), ctx);
            assertThat(result).isEmpty();
        }
    }

    // ── Hard take-profit (SHORT) ───────────────────────────────────────────────

    @Nested
    @DisplayName("Hard take-profit — SHORT position")
    class HardTakeProfitShort {

        @Test
        @DisplayName("Price at take-profit (below entry) → triggers exit")
        void atTakeProfit_triggers() {
            RiskContext ctx = RiskContext.shortPosition("a1", "BTCUSDT",
                    50_000, 1.0, null, 45_000.0, 0, 0);
            Optional<AgentDecision> result = guard.evaluate(candleAt(45_000), ctx);
            assertThat(result).isPresent();
            assertThat(result.get().action()).isEqualTo(Action.SELL);
            assertThat(result.get().reasoning()).contains("take-profit");
        }
    }

    // ── Percentage-based stop-loss ──────────────────────────────────────────────

    @Nested
    @DisplayName("Percentage-based max-loss")
    class PercentageStopLoss {

        @Test
        @DisplayName("LONG: 3% loss → triggers when maxLossPercent=2%")
        void longMaxLoss_triggers() {
            RiskContext ctx = RiskContext.longPosition("a1", "BTCUSDT",
                    50_000, 1.0, null, null, 2.0, 0);
            // 3% below entry = 48_500
            Optional<AgentDecision> result = guard.evaluate(candleAt(48_500), ctx);
            assertThat(result).isPresent();
            assertThat(result.get().reasoning()).contains("Max-loss");
        }

        @Test
        @DisplayName("LONG: 1% loss → no trigger when maxLossPercent=2%")
        void longWithinLoss_noTrigger() {
            RiskContext ctx = RiskContext.longPosition("a1", "BTCUSDT",
                    50_000, 1.0, null, null, 2.0, 0);
            // 1% below entry = 49_500
            Optional<AgentDecision> result = guard.evaluate(candleAt(49_500), ctx);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("SHORT: 3% loss → triggers when maxLossPercent=2%")
        void shortMaxLoss_triggers() {
            RiskContext ctx = RiskContext.shortPosition("a1", "BTCUSDT",
                    50_000, 1.0, null, null, 2.0, 0);
            // 3% above entry = 51_500
            Optional<AgentDecision> result = guard.evaluate(candleAt(51_500), ctx);
            assertThat(result).isPresent();
            assertThat(result.get().reasoning()).contains("Max-loss");
        }
    }

    // ── Percentage-based take-profit ────────────────────────────────────────────

    @Nested
    @DisplayName("Percentage-based max-gain")
    class PercentageMaxGain {

        @Test
        @DisplayName("LONG: 6% gain → triggers when maxGainPercent=5%")
        void longMaxGain_triggers() {
            RiskContext ctx = RiskContext.longPosition("a1", "BTCUSDT",
                    50_000, 1.0, null, null, 0, 5.0);
            // 6% above = 53_000
            Optional<AgentDecision> result = guard.evaluate(candleAt(53_000), ctx);
            assertThat(result).isPresent();
            assertThat(result.get().reasoning()).contains("Max-gain");
        }

        @Test
        @DisplayName("LONG: 3% gain → no trigger when maxGainPercent=5%")
        void longWithinGain_noTrigger() {
            RiskContext ctx = RiskContext.longPosition("a1", "BTCUSDT",
                    50_000, 1.0, null, null, 0, 5.0);
            // 3% above = 51_500
            Optional<AgentDecision> result = guard.evaluate(candleAt(51_500), ctx);
            assertThat(result).isEmpty();
        }
    }

    // ── Combined: stop-loss takes priority over take-profit ─────────────────────

    @Test
    @DisplayName("Both SL and TP configured — SL checked first (price hits SL)")
    void bothConfigured_slFirst() {
        RiskContext ctx = RiskContext.longPosition("a1", "BTCUSDT",
                50_000, 1.0, 48_000.0, 55_000.0, 2.0, 5.0);
        Optional<AgentDecision> result = guard.evaluate(candleAt(47_000), ctx);
        assertThat(result).isPresent();
        assertThat(result.get().reasoning()).contains("stop-loss");
    }

    @Test
    @DisplayName("Both SL and TP configured — TP hit (price above TP)")
    void bothConfigured_tpHit() {
        RiskContext ctx = RiskContext.longPosition("a1", "BTCUSDT",
                50_000, 1.0, 48_000.0, 55_000.0, 2.0, 5.0);
        Optional<AgentDecision> result = guard.evaluate(candleAt(56_000), ctx);
        assertThat(result).isPresent();
        assertThat(result.get().reasoning()).contains("take-profit");
    }

    @Test
    @DisplayName("Both configured — price in safe zone → no trigger")
    void bothConfigured_safeZone() {
        RiskContext ctx = RiskContext.longPosition("a1", "BTCUSDT",
                50_000, 1.0, 48_000.0, 55_000.0, 2.0, 5.0);
        Optional<AgentDecision> result = guard.evaluate(candleAt(51_000), ctx);
        assertThat(result).isEmpty();
    }
}
