package tradingbot.bot.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Ticker24hrStats DTO
 */
class Ticker24hrStatsTest {

    @Test
    @DisplayName("Builder creates Ticker24hrStats with all fields")
    void testBuilderWithAllFields() {
        Ticker24hrStats stats = Ticker24hrStats.builder()
            .symbol("BTCUSDT")
            .volume(1500.5)
            .quoteVolume(75000000.0)
            .priceChange(1500.0)
            .priceChangePercent(3.0)
            .highPrice(51500.0)
            .lowPrice(49000.0)
            .openPrice(50000.0)
            .lastPrice(51500.0)
            .build();
        
        assertEquals("BTCUSDT", stats.getSymbol());
        assertEquals(1500.5, stats.getVolume(), 0.01);
        assertEquals(75000000.0, stats.getQuoteVolume(), 0.01);
        assertEquals(1500.0, stats.getPriceChange(), 0.01);
        assertEquals(3.0, stats.getPriceChangePercent(), 0.01);
        assertEquals(51500.0, stats.getHighPrice(), 0.01);
        assertEquals(49000.0, stats.getLowPrice(), 0.01);
        assertEquals(50000.0, stats.getOpenPrice(), 0.01);
        assertEquals(51500.0, stats.getLastPrice(), 0.01);
    }

    @Test
    @DisplayName("Positive price change scenario")
    void testPositivePriceChange() {
        Ticker24hrStats stats = Ticker24hrStats.builder()
            .symbol("ETHUSDT")
            .volume(50000.0)
            .quoteVolume(150000000.0)
            .priceChange(150.0)
            .priceChangePercent(5.0)
            .highPrice(3150.0)
            .lowPrice(2900.0)
            .openPrice(3000.0)
            .lastPrice(3150.0)
            .build();
        
        assertTrue(stats.getPriceChange() > 0);
        assertTrue(stats.getPriceChangePercent() > 0);
        assertTrue(stats.getLastPrice() > stats.getOpenPrice());
        assertEquals(stats.getLastPrice(), stats.getHighPrice(), 0.01);
    }

    @Test
    @DisplayName("Negative price change scenario")
    void testNegativePriceChange() {
        Ticker24hrStats stats = Ticker24hrStats.builder()
            .symbol("SOLUSDT")
            .volume(100000.0)
            .quoteVolume(9500000.0)
            .priceChange(-5.0)
            .priceChangePercent(-5.0)
            .highPrice(105.0)
            .lowPrice(95.0)
            .openPrice(100.0)
            .lastPrice(95.0)
            .build();
        
        assertTrue(stats.getPriceChange() < 0);
        assertTrue(stats.getPriceChangePercent() < 0);
        assertTrue(stats.getLastPrice() < stats.getOpenPrice());
        assertEquals(stats.getLastPrice(), stats.getLowPrice(), 0.01);
    }

    @Test
    @DisplayName("Zero price change scenario")
    void testZeroPriceChange() {
        Ticker24hrStats stats = Ticker24hrStats.builder()
            .symbol("ADAUSDT")
            .volume(1000000.0)
            .quoteVolume(500000.0)
            .priceChange(0.0)
            .priceChangePercent(0.0)
            .highPrice(0.52)
            .lowPrice(0.48)
            .openPrice(0.50)
            .lastPrice(0.50)
            .build();
        
        assertEquals(0.0, stats.getPriceChange(), 0.001);
        assertEquals(0.0, stats.getPriceChangePercent(), 0.001);
        assertEquals(stats.getOpenPrice(), stats.getLastPrice(), 0.001);
    }

    @Test
    @DisplayName("High volume trading day")
    void testHighVolumeTradingDay() {
        Ticker24hrStats stats = Ticker24hrStats.builder()
            .symbol("BTCUSDT")
            .volume(25000.0) // Very high volume
            .quoteVolume(1250000000.0)
            .priceChange(2500.0)
            .priceChangePercent(5.0)
            .highPrice(52500.0)
            .lowPrice(48000.0)
            .openPrice(50000.0)
            .lastPrice(52500.0)
            .build();
        
        assertTrue(stats.getVolume() > 10000);
        assertTrue(stats.getQuoteVolume() > 1000000000);
        // Quote volume should be roughly volume * average price
        double avgPrice = (stats.getHighPrice() + stats.getLowPrice()) / 2;
        double approxQuoteVolume = stats.getVolume() * avgPrice;
        // Allow wide tolerance since quote volume can be calculated different ways
        assertTrue(Math.abs(stats.getQuoteVolume() - approxQuoteVolume) < stats.getQuoteVolume() * 0.5);
    }

    @Test
    @DisplayName("Low volatility scenario")
    void testLowVolatilityScenario() {
        Ticker24hrStats stats = Ticker24hrStats.builder()
            .symbol("USDCUSDT")
            .volume(10000000.0)
            .quoteVolume(10000000.0)
            .priceChange(0.0001)
            .priceChangePercent(0.01)
            .highPrice(1.0001)
            .lowPrice(0.9999)
            .openPrice(1.0000)
            .lastPrice(1.0001)
            .build();
        
        assertTrue(Math.abs(stats.getPriceChangePercent()) < 0.1);
        double priceRange = stats.getHighPrice() - stats.getLowPrice();
        assertTrue(priceRange < 0.01);
    }

    @Test
    @DisplayName("High volatility scenario")
    void testHighVolatilityScenario() {
        Ticker24hrStats stats = Ticker24hrStats.builder()
            .symbol("DOGEUSDT")
            .volume(1000000000.0)
            .quoteVolume(100000000.0)
            .priceChange(0.02)
            .priceChangePercent(20.0)
            .highPrice(0.12)
            .lowPrice(0.08)
            .openPrice(0.10)
            .lastPrice(0.12)
            .build();
        
        assertTrue(Math.abs(stats.getPriceChangePercent()) > 10);
        double priceRange = stats.getHighPrice() - stats.getLowPrice();
        double rangePercent = (priceRange / stats.getOpenPrice()) * 100;
        assertTrue(rangePercent > 30);
    }

    @Test
    @DisplayName("Price change percent calculation is consistent")
    void testPriceChangePercentConsistency() {
        Ticker24hrStats stats = Ticker24hrStats.builder()
            .symbol("LINKUSDT")
            .volume(500000.0)
            .quoteVolume(7500000.0)
            .priceChange(1.50)
            .priceChangePercent(10.0)
            .highPrice(16.50)
            .lowPrice(14.00)
            .openPrice(15.00)
            .lastPrice(16.50)
            .build();
        
        double calculatedPercent = (stats.getPriceChange() / stats.getOpenPrice()) * 100;
        assertEquals(stats.getPriceChangePercent(), calculatedPercent, 0.1);
    }

    @Test
    @DisplayName("High-low range validation")
    void testHighLowRangeValidation() {
        Ticker24hrStats stats = Ticker24hrStats.builder()
            .symbol("BNBUSDT")
            .volume(50000.0)
            .quoteVolume(15000000.0)
            .priceChange(20.0)
            .priceChangePercent(6.67)
            .highPrice(320.0)
            .lowPrice(280.0)
            .openPrice(300.0)
            .lastPrice(320.0)
            .build();
        
        assertTrue(stats.getHighPrice() >= stats.getLastPrice());
        assertTrue(stats.getLowPrice() <= stats.getLastPrice());
        assertTrue(stats.getHighPrice() > stats.getLowPrice());
        assertTrue(stats.getLastPrice() >= stats.getLowPrice() && stats.getLastPrice() <= stats.getHighPrice());
    }
}
