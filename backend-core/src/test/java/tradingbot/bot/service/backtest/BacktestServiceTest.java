package tradingbot.bot.service.backtest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import tradingbot.agent.ReactiveTradingAgent;
import tradingbot.agent.TradingAgentFactory;
import tradingbot.bot.service.BinanceFuturesService.Candle;
import tradingbot.bot.service.backtest.BacktestAgentExecutionService.ExecutionResult;
import tradingbot.bot.service.backtest.BacktestMetricsCalculator.BacktestMetrics;
import tradingbot.config.TradingConfig;

class BacktestServiceTest {

    @Mock private HistoricalDataLoader dataLoader;
    @Mock private TradingAgentFactory agentFactory;
    @Mock private BacktestAgentExecutionService executionService;
    @Mock private BacktestMetricsCalculator metricsCalculator;
    @Mock private BacktestRunRegistry runRegistry;
    @Mock private ReactiveTradingAgent mockAgent;

    private BacktestService backtestService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        backtestService = new BacktestService(dataLoader, agentFactory, executionService, metricsCalculator, runRegistry);
    }

    @Test
    void shouldRunBacktestSuccessfully() {
        List<Candle> history = buildHistory(200);
        when(dataLoader.loadFromCsv(anyString())).thenReturn(history);
        when(agentFactory.create(any())).thenReturn(mockAgent);

        List<EquityCurvePoint> curve = List.of(
                new EquityCurvePoint(0, Instant.ofEpochMilli(1000L), BigDecimal.valueOf(10_000.0), 0.0, "HOLD", "BTCUSDT"),
                new EquityCurvePoint(1, Instant.ofEpochMilli(61000L), BigDecimal.valueOf(10_050.0), 0.0, "BUY",  "BTCUSDT"));
        ExecutionResult execResult = new ExecutionResult(List.of(), curve, 200);
        when(executionService.execute(any(), anyList(), any(), any())).thenReturn(execResult);

        BacktestMetrics expected = new BacktestMetrics(
                "test-run-id", 10_050.0, 50.0, 0, Double.NaN, 1.0, 0.0, 0.0, curve, List.of());
        when(metricsCalculator.calculate(any(), anyDouble())).thenReturn(expected);

        TradingConfig config = buildConfig();
        BacktestMetrics result = backtestService.runBacktest("dummy.csv", config, 0, 0.0, 0.0004);

        assertNotNull(result);
        assertEquals(10_050.0, result.finalBalance());
        verify(agentFactory).create(config);
        verify(executionService).execute(eq(mockAgent), eq(history), eq(config), any());
        verify(metricsCalculator).calculate(eq(execResult), anyDouble());
    }

    @Test
    void shouldThrowExceptionIfNoData() {
        when(dataLoader.loadFromCsv(anyString())).thenReturn(new ArrayList<>());
        TradingConfig config = buildConfig();

        assertThrows(RuntimeException.class,
                () -> backtestService.runBacktest("empty.csv", config, 0, 0.0, 0.0004));
        verifyNoInteractions(agentFactory, executionService, metricsCalculator);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static List<Candle> buildHistory(int count) {
        List<Candle> history = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Candle c = new Candle();
            c.setOpenTime(1000L + (long) i * 60_000);
            c.setCloseTime(1000L + (long) i * 60_000 + 59_999);
            c.setOpen(new BigDecimal("50000"));
            c.setHigh(new BigDecimal("51000"));
            c.setLow(new BigDecimal("49000"));
            c.setClose(new BigDecimal(50000 + (i % 2 == 0 ? 100 : -100)));
            c.setVolume(new BigDecimal("1000"));
            history.add(c);
        }
        return history;
    }

    private static TradingConfig buildConfig() {
        TradingConfig config = new TradingConfig();
        config.setSymbol("BTCUSDT");
        config.setTradeAmount(0.1);
        config.setTrailingStopPercent(1.0);
        config.setLeverage(1);
        config.setLookbackPeriodRsi(14);
        config.setMacdFastPeriod(12);
        config.setMacdSlowPeriod(26);
        config.setMacdSignalPeriod(9);
        config.setBbPeriod(20);
        config.setBbStandardDeviation(2.0);
        config.setRsiOversoldThreshold(30);
        config.setRsiOverboughtThreshold(70);
        config.setInterval(900);
        return config;
    }
}
