package tradingbot.bot.service.backtest;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tradingbot.bot.service.BinanceFuturesService.Candle;

class BacktestExchangeServiceTest {

    private BacktestExchangeService exchangeService;
    private List<Candle> history;

    @BeforeEach
    void setUp() {
        exchangeService = new BacktestExchangeService(0, 0.0, 0.0004); // No latency, no slippage, 0.04% fee
        history = new ArrayList<>();
        
        // Create some dummy candles
        for (int i = 0; i < 10; i++) {
            Candle candle = new Candle();
            candle.setOpenTime(1000L + i * 60000); // 1 minute intervals
            candle.setCloseTime(1000L + (i + 1) * 60000);
            BigDecimal price = new BigDecimal(50000 + i * 100);
            candle.setOpen(price);
            candle.setClose(price);
            candle.setHigh(price.add(new BigDecimal(50)));
            candle.setLow(price.subtract(new BigDecimal(50)));
            history.add(candle);
        }
    }

    @Test
    void shouldUpdateCurrentPriceBasedOnContext() {
        exchangeService.setMarketContext(history, 0);
        assertEquals(50000.0, exchangeService.getCurrentPrice("BTCUSDT"));

        exchangeService.setMarketContext(history, 5);
        assertEquals(50500.0, exchangeService.getCurrentPrice("BTCUSDT"));
    }

    @Test
    void shouldExecuteLongOrderImmediatelyWithoutLatency() {
        exchangeService.setMarketContext(history, 0);
        exchangeService.enterLongPosition("BTCUSDT", 0.1);
        
        // Process pending orders (should execute immediately as latency is 0)
        exchangeService.processPendingOrders();
        
        // Open Price = 50000, with random slippage 0.05%-0.1%
        // Worst case: 50000 * 1.001 = 50050
        // Margin = 0.1 * 50050 = 5005
        // Fee = 5005 * 0.0004 = 2.002
        // Balance = 10000 - 5005 - 2.002 = 4992.998
        // Allow wider tolerance for random slippage
        assertTrue(exchangeService.getMarginBalance() >= 4992.0 && exchangeService.getMarginBalance() <= 4998.0);
    }

    @Test
    void shouldExecuteShortOrderWithLatency() {
        exchangeService = new BacktestExchangeService(1000, 0.0, 0.0004); // 1000ms latency, 0.04% fee
        exchangeService.setMarketContext(history, 0); // Time 1000
        exchangeService.enterShortPosition("BTCUSDT", 0.1);
        
        exchangeService.processPendingOrders(); // Time 1000 < 1000 + 1000. Should not execute.
        assertEquals(10000.0, exchangeService.getMarginBalance());
        
        exchangeService.setMarketContext(history, 1); // Time 1000 + 60000 = 61000 > 2000.
        exchangeService.processPendingOrders();
        
        // Executes at Open price of index 1 (50100) with random slippage
        // For SELL: price * (1 - slippage), so slightly worse fill
        // Worst case: 50100 * 0.999 = 50050
        // Margin = 0.1 * 50050 = 5005, Fee = 2.002
        // Balance range: 4992.998 to 4987.5
        assertTrue(exchangeService.getMarginBalance() >= 4987.0 && exchangeService.getMarginBalance() <= 4993.0);
    }

    @Test
    void shouldApplyRandomSlippage() {
        exchangeService = new BacktestExchangeService(0, 0.0, 0.0004); // Random slippage (0.05%-0.1%), 0.04% fee
        exchangeService.setMarketContext(history, 0); // Open Price 50000
        
        // Buy with random slippage between 0.05% and 0.1%
        exchangeService.enterLongPosition("BTCUSDT", 0.1);
        exchangeService.processPendingOrders();
        
        // Min slippage: 50000 * 1.0005 = 50025, Margin = 5002.5, Fee = 2.001, Balance = 4995.499
        // Max slippage: 50000 * 1.001 = 50050, Margin = 5005, Fee = 2.002, Balance = 4992.998
        assertTrue(exchangeService.getMarginBalance() >= 4992.0 && exchangeService.getMarginBalance() <= 4996.0);
    }
    
    @Test
    void shouldCalculateProfitOnExit() {
        exchangeService.setMarketContext(history, 0); // Open Price 50000
        exchangeService.enterLongPosition("BTCUSDT", 0.1);
        exchangeService.processPendingOrders();
        
        double balanceAfterEntry = exchangeService.getMarginBalance();
        
        exchangeService.setMarketContext(history, 5); // Open Price 50500
        exchangeService.exitLongPosition("BTCUSDT", 0.1);
        exchangeService.processPendingOrders();
        
        // Entry: ~50000-50050 (with slippage), Exit: ~50450-50500 (sell slippage worse)
        // Net PnL should be positive but less than ideal
        // Expected range: 10040 to 10048 (accounting for both entry and exit slippage)
        assertTrue(exchangeService.getMarginBalance() >= 10035.0 && exchangeService.getMarginBalance() <= 10050.0);
    }

    @Test
    void shouldTriggerLiquidationLong() {
        exchangeService.setLeverage("BTCUSDT", 10); // 10x leverage
        exchangeService.setMarketContext(history, 0); // Price 50000
        exchangeService.enterLongPosition("BTCUSDT", 1.0); // Size 1 BTC, Margin 5000
        exchangeService.processPendingOrders();
        
        // Balance = 10000 - 5000 - fee (~20) = 4980
        assertTrue(exchangeService.getMarginBalance() < 5000); 
        
        // Liquidation price = 50000 * (1 - 0.1) = 45000
        // Add a crash candle
        Candle crashCandle = new Candle();
        crashCandle.setOpenTime(history.get(9).getCloseTime());
        crashCandle.setCloseTime(history.get(9).getCloseTime() + 60000);
        crashCandle.setOpen(new BigDecimal(48000));
        crashCandle.setClose(new BigDecimal(48000));
        crashCandle.setHigh(new BigDecimal(48500));
        crashCandle.setLow(new BigDecimal(44000)); // Low < 45000
        history.add(crashCandle);
        
        exchangeService.setMarketContext(history, 10); // Process liquidation
        
        // Margin should NOT be returned.
        // Balance should remain around 4980.
        // If we hadn't liquidated and exited at 48000:
        // PnL = (48000 - 50000) * 1 = -2000.
        // Returned = 5000 - 2000 = 3000.
        // Balance would be 4980 + 3000 = 7980.
        
        // Since we liquidated, we assume total margin loss (simple model).
        assertTrue(exchangeService.getMarginBalance() < 6000); // Definitely less than if we just exited with loss
        
        // Attempt to exit should do nothing as position is gone
        double balance = exchangeService.getMarginBalance();
        exchangeService.exitLongPosition("BTCUSDT", 1.0);
        exchangeService.processPendingOrders();
        assertEquals(balance, exchangeService.getMarginBalance());
    }

    @Test
    void shouldProcessOrdersAutomaticallyOnNextStep() {
        exchangeService.setMarketContext(history, 0);
        exchangeService.enterLongPosition("BTCUSDT", 0.1);
        
        // Move to next step (simulating runner loop)
        exchangeService.setMarketContext(history, 1);
        
        // Should have executed automatically
        // Balance should decrease (margin used)
        assertTrue(exchangeService.getMarginBalance() < 10000);
    }

    @Test
    void shouldApplySlippageOnlyToEntryPrice() {
        exchangeService.setMarketContext(history, 0); // Open Price 50000
        double initialBalance = exchangeService.getMarginBalance();
        
        // Enter position
        exchangeService.enterLongPosition("BTCUSDT", 0.1);
        exchangeService.processPendingOrders();
        
        // Verify slippage was applied (balance should be slightly less than ideal)
        double balanceAfterEntry = exchangeService.getMarginBalance();
        double idealMargin = 0.1 * 50000; // 5000
        double idealFee = idealMargin * 0.0004; // 2.0
        double idealBalance = initialBalance - idealMargin - idealFee; // 4998.0
        
        // With slippage, actual balance should be slightly worse
        assertTrue(balanceAfterEntry <= idealBalance);
    }

    @Test
    void shouldUseOpenPriceNotClosePrice() {
        // Create a candle where Open and Close differ significantly
        Candle volatileCandle = new Candle();
        volatileCandle.setOpenTime(1000L);
        volatileCandle.setCloseTime(60000L);
        volatileCandle.setOpen(new BigDecimal(50000)); // Opens at 50000
        volatileCandle.setClose(new BigDecimal(51000)); // Closes at 51000 (pump)
        volatileCandle.setHigh(new BigDecimal(51500));
        volatileCandle.setLow(new BigDecimal(49500));
        
        List<Candle> testHistory = new ArrayList<>();
        testHistory.add(volatileCandle);
        
        exchangeService.setMarketContext(testHistory, 0);
        exchangeService.enterLongPosition("BTCUSDT", 0.1);
        exchangeService.processPendingOrders();
        
        // Should execute around 50000 (Open), not 51000 (Close)
        // Max with slippage: 50050, Margin = 5005, Fee = 2.002
        // Balance should be close to 4992.998
        assertTrue(exchangeService.getMarginBalance() >= 4992.0 && exchangeService.getMarginBalance() <= 4998.0);
        
        // If it used Close (51000), balance would be much lower (~4897)
        assertTrue(exchangeService.getMarginBalance() > 4900);
    }

    @Test
    void shouldApplyDifferentSlippageOnEachTrade() {
        // Execute multiple trades and verify slippage varies
        List<Double> balances = new ArrayList<>();
        
        for (int trial = 0; trial < 5; trial++) {
            BacktestExchangeService trialService = new BacktestExchangeService(0, 0.0, 0.0004);
            trialService.setMarketContext(history, 0);
            trialService.enterLongPosition("BTCUSDT", 0.1);
            trialService.processPendingOrders();
            balances.add(trialService.getMarginBalance());
        }
        
        // At least some balances should differ (probability of all being equal with random slippage is negligible)
        long uniqueBalances = balances.stream().distinct().count();
        assertTrue(uniqueBalances >= 2, "Expected different slippage values across trades");
    }

    @Test
    void shouldHandleShortPositionSlippage() {
        double initialBalance = exchangeService.getMarginBalance();
        exchangeService.setMarketContext(history, 0); // Open Price 50000
        exchangeService.enterShortPosition("BTCUSDT", 0.1);
        exchangeService.processPendingOrders();
        
        // For SHORT (SELL), slippage makes price worse: price * (1- slippage)
        // Entry execution: 50000 * (1 - 0.0005 to 0.001) = 49975 to 49950
        // Margin should be deducted from initial balance (10000)
        // Verify margin was deducted (balance decreased) and is reasonable
        double newBalance = exchangeService.getMarginBalance();
        assertTrue(newBalance < initialBalance, "Balance should decrease after entering position");
        assertTrue(newBalance > 4900.0 && newBalance < 5100.0, "Balance should be in reasonable range after short entry");
    }

    @Test
    void shouldSimulateRealisticRoundTripWithSlippage() {
        // Full round-trip: Entry -> Exit with realistic slippage on both sides
        exchangeService.setMarketContext(history, 0); // Open 50000
        exchangeService.enterLongPosition("BTCUSDT", 0.1);
        exchangeService.processPendingOrders();
        
        double entryBalance = exchangeService.getMarginBalance();
        
        // Price increases by 1%
        exchangeService.setMarketContext(history, 5); // Open 50500
        exchangeService.exitLongPosition("BTCUSDT", 0.1);
        exchangeService.processPendingOrders();
        
        double finalBalance = exchangeService.getMarginBalance();
        double profitLoss = finalBalance - 10000;
        
        // Expected PnL: ~500 (1% of 50000) minus slippage costs and fees
        // Slippage costs: ~25-50 (0.05%-0.1% on entry + exit)
        // Fees: ~4 total
        // Net: 500 - 50 - 4 = ~446 to ~471
        assertTrue(profitLoss >= 35.0 && profitLoss <= 50.0, 
            "Expected profit between $35-50 after slippage, got: " + profitLoss);
    }

    @Test
    void shouldHandleZeroQuantityGracefully() {
        exchangeService.setMarketContext(history, 0);
        exchangeService.enterLongPosition("BTCUSDT", 0.0);
        exchangeService.processPendingOrders();
        
        // Balance should remain unchanged
        assertEquals(10000.0, exchangeService.getMarginBalance(), 0.01);
    }

    @Test
    void shouldNormalizeQuantityProperly() {
        exchangeService.setMarketContext(history, 0);
        // Enter with non-normalized quantity
        exchangeService.enterLongPosition("BTCUSDT", 0.1234567);
        exchangeService.processPendingOrders();
        
        // Should normalize to 0.123 (step size 0.001)
        // Execution: ~50000 * 0.123 = 6150, Balance should reflect this
        assertTrue(exchangeService.getMarginBalance() < 4000);
    }
}
