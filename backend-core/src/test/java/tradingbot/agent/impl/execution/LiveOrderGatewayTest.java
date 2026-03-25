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
 * Unit tests for {@link LiveOrderGateway}.
 */
class LiveOrderGatewayTest {

    private FuturesExchangeService exchange;
    private AtomicReference<RiskContext> riskCtxRef;
    private LiveOrderGateway gateway;

    @BeforeEach
    void setUp() {
        exchange = mock(FuturesExchangeService.class);
        riskCtxRef = new AtomicReference<>(RiskContext.noPosition("live-agent", "BTCUSDT"));
        gateway = new LiveOrderGateway(exchange);
        gateway.setRiskContextUpdater(riskCtxRef::set);
        // Default: sufficient margin
        when(exchange.getMarginBalance()).thenReturn(10000.0);
    }

    private AgentDecision buyDecision() {
        return AgentDecision.of("live-agent", "BTCUSDT", Action.BUY, 80, "Test buy");
    }

    private AgentDecision sellDecision() {
        return AgentDecision.of("live-agent", "BTCUSDT", Action.SELL, 80, "Test sell");
    }

    private AgentDecision holdDecision() {
        return AgentDecision.of("live-agent", "BTCUSDT", Action.HOLD, 50, "Hold");
    }

    private OrderResult mockFill(String side, double price) {
        return OrderResult.builder()
                .exchangeOrderId("LIVE-001")
                .symbol("BTCUSDT")
                .side(side)
                .status(OrderResult.OrderStatus.FILLED)
                .orderedQuantity(0.001)
                .filledQuantity(0.001)
                .avgFillPrice(price)
                .commission(0.01)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ── HOLD ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("HOLD decisions")
    class HoldTests {
        @Test
        void holdReturnsNoop() {
            ExecutionResult result = gateway.execute(holdDecision(), "BTCUSDT", 50000);
            assertThat(result.action()).isEqualTo(ExecutionAction.NOOP);
            verify(exchange, never()).enterLongPosition(anyString(), anyDouble());
        }
    }

    // ── BUY from flat ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BUY from flat")
    class BuyFromFlatTests {

        @Test
        @DisplayName("BUY when flat + sufficient margin → ENTER_LONG + bracket orders")
        void buyEntersLongWithBrackets() {
            when(exchange.enterLongPosition(eq("BTCUSDT"), anyDouble())).thenReturn(mockFill("Buy", 50000));
            // Bracket SL/TP stubs
            when(exchange.placeStopLossOrder(anyString(), anyString(), anyDouble(), anyDouble()))
                    .thenReturn(mockFill("Sell", 49000));
            when(exchange.placeTakeProfitOrder(anyString(), anyString(), anyDouble(), anyDouble()))
                    .thenReturn(mockFill("Sell", 52500));

            ExecutionResult result = gateway.execute(buyDecision(), "BTCUSDT", 50000);

            assertThat(result.action()).isEqualTo(ExecutionAction.ENTER_LONG);
            assertThat(result.success()).isTrue();
            assertThat(gateway.hasOpenPosition("BTCUSDT")).isTrue();

            // Verify bracket orders were placed
            verify(exchange).placeStopLossOrder(eq("BTCUSDT"), eq("Sell"), anyDouble(), anyDouble());
            verify(exchange).placeTakeProfitOrder(eq("BTCUSDT"), eq("Sell"), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("BUY when flat + insufficient margin → failed")
        void buyInsufficientMargin() {
            when(exchange.getMarginBalance()).thenReturn(10.0); // below MIN_MARGIN_USDT

            ExecutionResult result = gateway.execute(buyDecision(), "BTCUSDT", 50000);

            assertThat(result.success()).isFalse();
            assertThat(result.action()).isEqualTo(ExecutionAction.ENTER_LONG);
            verify(exchange, never()).enterLongPosition(anyString(), anyDouble());
        }
    }

    // ── BUY when already LONG ──────────────────────────────────────────────────

    @Nested
    @DisplayName("BUY when already LONG")
    class BuyWhenLongTests {

        @Test
        void noPyramiding() {
            when(exchange.enterLongPosition(eq("BTCUSDT"), anyDouble())).thenReturn(mockFill("Buy", 50000));
            when(exchange.placeStopLossOrder(anyString(), anyString(), anyDouble(), anyDouble()))
                    .thenReturn(mockFill("Sell", 49000));
            when(exchange.placeTakeProfitOrder(anyString(), anyString(), anyDouble(), anyDouble()))
                    .thenReturn(mockFill("Sell", 52500));
            gateway.execute(buyDecision(), "BTCUSDT", 50000); // enter LONG

            ExecutionResult result = gateway.execute(buyDecision(), "BTCUSDT", 51000);

            assertThat(result.action()).isEqualTo(ExecutionAction.NOOP);
        }
    }

    // ── SELL when LONG ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SELL when LONG")
    class SellWhenLongTests {

        @Test
        @DisplayName("SELL when LONG → EXIT_LONG with PnL")
        void sellClosesLong() {
            when(exchange.enterLongPosition(eq("BTCUSDT"), anyDouble())).thenReturn(mockFill("Buy", 50000));
            when(exchange.placeStopLossOrder(anyString(), anyString(), anyDouble(), anyDouble()))
                    .thenReturn(mockFill("Sell", 49000));
            when(exchange.placeTakeProfitOrder(anyString(), anyString(), anyDouble(), anyDouble()))
                    .thenReturn(mockFill("Sell", 52500));
            gateway.execute(buyDecision(), "BTCUSDT", 50000);

            when(exchange.exitLongPosition(eq("BTCUSDT"), anyDouble())).thenReturn(mockFill("Sell", 51000));
            when(exchange.getMarginBalance()).thenReturn(10000.0).thenReturn(11000.0);

            ExecutionResult result = gateway.execute(sellDecision(), "BTCUSDT", 51000);

            assertThat(result.action()).isEqualTo(ExecutionAction.EXIT_LONG);
            assertThat(result.success()).isTrue();
            assertThat(gateway.hasOpenPosition("BTCUSDT")).isFalse();
        }
    }

    // ── SELL from flat ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SELL from flat")
    class SellFromFlatTests {

        @Test
        @DisplayName("SELL when flat → ENTER_SHORT + bracket orders")
        void sellEntersShort() {
            when(exchange.enterShortPosition(eq("BTCUSDT"), anyDouble())).thenReturn(mockFill("Sell", 50000));
            when(exchange.placeStopLossOrder(anyString(), anyString(), anyDouble(), anyDouble()))
                    .thenReturn(mockFill("Buy", 51000));
            when(exchange.placeTakeProfitOrder(anyString(), anyString(), anyDouble(), anyDouble()))
                    .thenReturn(mockFill("Buy", 47500));

            ExecutionResult result = gateway.execute(sellDecision(), "BTCUSDT", 50000);

            assertThat(result.action()).isEqualTo(ExecutionAction.ENTER_SHORT);
            assertThat(result.success()).isTrue();
            verify(exchange).placeStopLossOrder(eq("BTCUSDT"), eq("Buy"), anyDouble(), anyDouble());
            verify(exchange).placeTakeProfitOrder(eq("BTCUSDT"), eq("Buy"), anyDouble(), anyDouble());
        }
    }

    // ── RiskContext updates ────────────────────────────────────────────────────

    @Nested
    @DisplayName("RiskContext updates")
    class RiskContextTests {

        @Test
        @DisplayName("Entering LONG updates RiskContext with SL/TP prices")
        void enterLongUpdatesRiskContext() {
            when(exchange.enterLongPosition(eq("BTCUSDT"), anyDouble())).thenReturn(mockFill("Buy", 50000));
            when(exchange.placeStopLossOrder(anyString(), anyString(), anyDouble(), anyDouble()))
                    .thenReturn(mockFill("Sell", 49000));
            when(exchange.placeTakeProfitOrder(anyString(), anyString(), anyDouble(), anyDouble()))
                    .thenReturn(mockFill("Sell", 52500));

            gateway.execute(buyDecision(), "BTCUSDT", 50000);

            RiskContext ctx = riskCtxRef.get();
            assertThat(ctx.hasOpenPosition()).isTrue();
            assertThat(ctx.positionSide()).isEqualTo("LONG");
            assertThat(ctx.stopLossPrice()).isNotNull();
            assertThat(ctx.takeProfitPrice()).isNotNull();
        }

        @Test
        @DisplayName("Closing position resets RiskContext to noPosition")
        void closeResetsContext() {
            when(exchange.enterLongPosition(eq("BTCUSDT"), anyDouble())).thenReturn(mockFill("Buy", 50000));
            when(exchange.placeStopLossOrder(anyString(), anyString(), anyDouble(), anyDouble()))
                    .thenReturn(mockFill("Sell", 49000));
            when(exchange.placeTakeProfitOrder(anyString(), anyString(), anyDouble(), anyDouble()))
                    .thenReturn(mockFill("Sell", 52500));
            gateway.execute(buyDecision(), "BTCUSDT", 50000);

            when(exchange.exitLongPosition(eq("BTCUSDT"), anyDouble())).thenReturn(mockFill("Sell", 51000));
            when(exchange.getMarginBalance()).thenReturn(10000.0).thenReturn(11000.0);
            gateway.execute(sellDecision(), "BTCUSDT", 51000);

            RiskContext ctx = riskCtxRef.get();
            assertThat(ctx.hasOpenPosition()).isFalse();
        }
    }
}
