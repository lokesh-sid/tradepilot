package tradingbot.agent.impl.execution;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import tradingbot.agent.domain.execution.ExecutionResult;
import tradingbot.agent.domain.execution.ExecutionResult.ExecutionAction;
import tradingbot.agent.domain.model.AgentDecision;
import tradingbot.agent.domain.model.AgentDecision.Action;
import tradingbot.agent.domain.risk.RiskContext;
import tradingbot.bot.service.FuturesExchangeService;
import tradingbot.bot.service.OrderResult;

/**
 * Unit tests for {@link BacktestOrderGateway}.
 */
class BacktestOrderGatewayTest {

    private FuturesExchangeService exchange;
    private AtomicReference<RiskContext> riskCtxRef;
    private BacktestOrderGateway gateway;

    @BeforeEach
    void setUp() {
        exchange = mock(FuturesExchangeService.class);
        riskCtxRef = new AtomicReference<>(RiskContext.noPosition("test-agent", "BTCUSDT"));
        gateway = new BacktestOrderGateway(exchange, riskCtxRef::set);
    }

    private AgentDecision buyDecision() {
        return AgentDecision.of("test-agent", "BTCUSDT", Action.BUY, 80, "Test buy");
    }

    private AgentDecision sellDecision() {
        return AgentDecision.of("test-agent", "BTCUSDT", Action.SELL, 80, "Test sell");
    }

    private AgentDecision holdDecision() {
        return AgentDecision.of("test-agent", "BTCUSDT", Action.HOLD, 50, "Test hold");
    }

    private OrderResult mockFill(String side, double price) {
        return OrderResult.builder()
                .exchangeOrderId("BT-001")
                .symbol("BTCUSDT")
                .side(side)
                .status(OrderResult.OrderStatus.FILLED)
                .orderedQuantity(1.0)
                .filledQuantity(1.0)
                .avgFillPrice(price)
                .commission(0.0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ── HOLD ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HOLD decisions")
    class HoldTests {

        @Test
        @DisplayName("HOLD returns NOOP and does not touch exchange")
        void holdReturnsNoop() {
            ExecutionResult result = gateway.execute(holdDecision(), "BTCUSDT", 50000);

            assertThat(result.action()).isEqualTo(ExecutionAction.NOOP);
            assertThat(result.success()).isTrue();
            verify(exchange, never()).enterLongPosition(anyString(), anyDouble());
            verify(exchange, never()).exitLongPosition(anyString(), anyDouble());
        }
    }

    // ── BUY when flat ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BUY from flat position")
    class BuyFromFlatTests {

        @Test
        @DisplayName("BUY when flat → ENTER_LONG")
        void buyWhenFlat() {
            when(exchange.enterLongPosition("BTCUSDT", 1.0)).thenReturn(mockFill("Buy", 50000));

            ExecutionResult result = gateway.execute(buyDecision(), "BTCUSDT", 50000);

            assertThat(result.action()).isEqualTo(ExecutionAction.ENTER_LONG);
            assertThat(result.success()).isTrue();
            assertThat(result.fillPrice()).isEqualTo(50000);
            assertThat(gateway.hasOpenPosition("BTCUSDT")).isTrue();
            assertThat(gateway.getPositionSide("BTCUSDT")).isEqualTo("LONG");
        }

        @Test
        @DisplayName("BUY when flat updates RiskContext to LONG")
        void buyUpdatesRiskContext() {
            when(exchange.enterLongPosition("BTCUSDT", 1.0)).thenReturn(mockFill("Buy", 50000));

            gateway.execute(buyDecision(), "BTCUSDT", 50000);

            RiskContext ctx = riskCtxRef.get();
            assertThat(ctx.hasOpenPosition()).isTrue();
            assertThat(ctx.positionSide()).isEqualTo("LONG");
            assertThat(ctx.entryPrice()).isEqualTo(50000);
        }
    }

    // ── BUY when already LONG ──────────────────────────────────────────────────

    @Nested
    @DisplayName("BUY when already LONG")
    class BuyWhenLongTests {

        @Test
        @DisplayName("BUY when LONG → NOOP (no pyramiding)")
        void buyWhenLongReturnsNoop() {
            when(exchange.enterLongPosition("BTCUSDT", 1.0)).thenReturn(mockFill("Buy", 50000));
            gateway.execute(buyDecision(), "BTCUSDT", 50000); // establish LONG

            ExecutionResult result = gateway.execute(buyDecision(), "BTCUSDT", 51000);

            assertThat(result.action()).isEqualTo(ExecutionAction.NOOP);
            assertThat(result.success()).isTrue();
        }
    }

    // ── SELL when flat ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SELL from flat position")
    class SellFromFlatTests {

        @Test
        @DisplayName("SELL when flat → ENTER_SHORT")
        void sellWhenFlat() {
            when(exchange.enterShortPosition("BTCUSDT", 1.0)).thenReturn(mockFill("Sell", 50000));

            ExecutionResult result = gateway.execute(sellDecision(), "BTCUSDT", 50000);

            assertThat(result.action()).isEqualTo(ExecutionAction.ENTER_SHORT);
            assertThat(result.success()).isTrue();
            assertThat(gateway.getPositionSide("BTCUSDT")).isEqualTo("SHORT");
        }
    }

    // ── SELL when LONG ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SELL when LONG (close)")
    class SellWhenLongTests {

        @Test
        @DisplayName("SELL when LONG → EXIT_LONG with PnL")
        void sellWhenLongClosesPosition() {
            when(exchange.enterLongPosition("BTCUSDT", 1.0)).thenReturn(mockFill("Buy", 50000));
            gateway.execute(buyDecision(), "BTCUSDT", 50000);

            when(exchange.exitLongPosition("BTCUSDT", 1.0)).thenReturn(mockFill("Sell", 51000));
            when(exchange.getMarginBalance()).thenReturn(10000.0).thenReturn(11000.0);

            ExecutionResult result = gateway.execute(sellDecision(), "BTCUSDT", 51000);

            assertThat(result.action()).isEqualTo(ExecutionAction.EXIT_LONG);
            assertThat(result.success()).isTrue();
            assertThat(result.fillPrice()).isEqualTo(51000);
            assertThat(result.realizedPnl()).isEqualTo(1000.0);
            assertThat(gateway.hasOpenPosition("BTCUSDT")).isFalse();
        }

        @Test
        @DisplayName("SELL when LONG clears RiskContext")
        void sellWhenLongClearsRiskContext() {
            when(exchange.enterLongPosition("BTCUSDT", 1.0)).thenReturn(mockFill("Buy", 50000));
            gateway.execute(buyDecision(), "BTCUSDT", 50000);

            when(exchange.exitLongPosition("BTCUSDT", 1.0)).thenReturn(mockFill("Sell", 51000));
            when(exchange.getMarginBalance()).thenReturn(10000.0).thenReturn(11000.0);
            gateway.execute(sellDecision(), "BTCUSDT", 51000);

            RiskContext ctx = riskCtxRef.get();
            assertThat(ctx.hasOpenPosition()).isFalse();
        }
    }

    // ── BUY when SHORT ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BUY when SHORT (close)")
    class BuyWhenShortTests {

        @Test
        @DisplayName("BUY when SHORT → EXIT_SHORT")
        void buyWhenShortClosesPosition() {
            when(exchange.enterShortPosition("BTCUSDT", 1.0)).thenReturn(mockFill("Sell", 50000));
            gateway.execute(sellDecision(), "BTCUSDT", 50000);

            when(exchange.exitShortPosition("BTCUSDT", 1.0)).thenReturn(mockFill("Buy", 49000));
            when(exchange.getMarginBalance()).thenReturn(10000.0).thenReturn(11000.0);

            ExecutionResult result = gateway.execute(buyDecision(), "BTCUSDT", 49000);

            assertThat(result.action()).isEqualTo(ExecutionAction.EXIT_SHORT);
            assertThat(result.success()).isTrue();
            assertThat(gateway.hasOpenPosition("BTCUSDT")).isFalse();
        }
    }

    // ── Exchange failure ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Exchange failures")
    class FailureTests {

        @Test
        @DisplayName("enterLongPosition throws → failed result, position unchanged")
        void enterLongFailure() {
            when(exchange.enterLongPosition("BTCUSDT", 1.0))
                    .thenThrow(new RuntimeException("Connection timeout"));

            ExecutionResult result = gateway.execute(buyDecision(), "BTCUSDT", 50000);

            assertThat(result.action()).isEqualTo(ExecutionAction.ENTER_LONG);
            assertThat(result.success()).isFalse();
            assertThat(gateway.hasOpenPosition("BTCUSDT")).isFalse();
        }
    }

    // ── Query methods ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Query methods")
    class QueryTests {

        @Test
        @DisplayName("getEntryPrice returns 0 when flat")
        void entryPriceFlat() {
            assertThat(gateway.getEntryPrice("BTCUSDT")).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getPositionQuantity returns 0 when flat")
        void quantityFlat() {
            assertThat(gateway.getPositionQuantity("BTCUSDT")).isEqualTo(0.0);
        }

        @Test
        @DisplayName("getPositionSide returns null when flat")
        void sideFlat() {
            assertThat(gateway.getPositionSide("BTCUSDT")).isNull();
        }
    }
}
