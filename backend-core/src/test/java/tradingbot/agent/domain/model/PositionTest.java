package tradingbot.agent.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Position domain model P&L calculations
 */
class PositionTest {

    private Position longPosition;
    private Position shortPosition;

    @BeforeEach
    void setUp() {
        longPosition = Position.builder()
            .id("pos-1")
            .agentId("agent-1")
            .symbol("BTCUSDT")
            .direction(TradeDirection.LONG)
            .entryPrice(50000.0)
            .quantity(0.1)
            .stopLoss(48000.0)
            .takeProfit(55000.0)
            .status(Position.PositionStatus.OPEN)
            .openedAt(Instant.now())
            .build();

        shortPosition = Position.builder()
            .id("pos-2")
            .agentId("agent-1")
            .symbol("ETHUSDT")
            .direction(TradeDirection.SHORT)
            .entryPrice(3000.0)
            .quantity(1.0)
            .stopLoss(3100.0)
            .takeProfit(2800.0)
            .status(Position.PositionStatus.OPEN)
            .openedAt(Instant.now())
            .build();
    }

    @Test
    @DisplayName("Long position - profitable scenario")
    void testLongPositionProfit() {
        double currentPrice = 52000.0; // $2000 above entry
        
        double unrealizedPnl = longPosition.calculateUnrealizedPnl(currentPrice);
        double unrealizedPnlPercent = longPosition.calculateUnrealizedPnlPercent(currentPrice);
        
        assertEquals(200.0, unrealizedPnl, 0.01); // (52000 - 50000) * 0.1
        assertEquals(4.0, unrealizedPnlPercent, 0.01); // (2000/50000) * 100
    }

    @Test
    @DisplayName("Long position - loss scenario")
    void testLongPositionLoss() {
        double currentPrice = 48000.0; // $2000 below entry (at stop-loss)
        
        double unrealizedPnl = longPosition.calculateUnrealizedPnl(currentPrice);
        double unrealizedPnlPercent = longPosition.calculateUnrealizedPnlPercent(currentPrice);
        
        assertEquals(-200.0, unrealizedPnl, 0.01); // (48000 - 50000) * 0.1
        assertEquals(-4.0, unrealizedPnlPercent, 0.01); // (-2000/50000) * 100
    }

    @Test
    @DisplayName("Short position - profitable scenario")
    void testShortPositionProfit() {
        double currentPrice = 2800.0; // $200 below entry (at take-profit)
        
        double unrealizedPnl = shortPosition.calculateUnrealizedPnl(currentPrice);
        double unrealizedPnlPercent = shortPosition.calculateUnrealizedPnlPercent(currentPrice);
        
        assertEquals(200.0, unrealizedPnl, 0.01); // (3000 - 2800) * 1.0
        assertEquals(6.67, unrealizedPnlPercent, 0.01); // (200/3000) * 100
    }

    @Test
    @DisplayName("Short position - loss scenario")
    void testShortPositionLoss() {
        double currentPrice = 3100.0; // $100 above entry (at stop-loss)
        
        double unrealizedPnl = shortPosition.calculateUnrealizedPnl(currentPrice);
        double unrealizedPnlPercent = shortPosition.calculateUnrealizedPnlPercent(currentPrice);
        
        assertEquals(-100.0, unrealizedPnl, 0.01); // (3000 - 3100) * 1.0
        assertEquals(-3.33, unrealizedPnlPercent, 0.01); // (-100/3000) * 100
    }

    @Test
    @DisplayName("Update monitoring updates timestamp and unrealized P&L")
    void testUpdateMonitoring() {
        Instant beforeUpdate = longPosition.getLastCheckedAt();
        
        longPosition.updateMonitoring(100.0);
        
        assertNotNull(longPosition.getLastCheckedAt());
        if (beforeUpdate != null) {
            assertTrue(longPosition.getLastCheckedAt().isAfter(beforeUpdate) || 
                      longPosition.getLastCheckedAt().equals(beforeUpdate));
        }
        assertEquals(100.0, longPosition.getLastUnrealizedPnl(), 0.01);
    }

    @Test
    @DisplayName("Close position updates all fields correctly")
    void testClosePosition() {
        double exitPrice = 52000.0;
        double realizedPnl = 200.0;
        
        longPosition.close(exitPrice, realizedPnl, Position.PositionStatus.CLOSED);
        
        assertEquals(Position.PositionStatus.CLOSED, longPosition.getStatus());
        assertEquals(exitPrice, longPosition.getExitPrice(), 0.01);
        assertEquals(realizedPnl, longPosition.getRealizedPnl(), 0.01);
        assertNotNull(longPosition.getClosedAt());
    }

    @Test
    @DisplayName("Close position as stopped out")
    void testStopLossTriggered() {
        double exitPrice = 48000.0; // Stop-loss price
        double realizedPnl = -200.0;
        
        longPosition.close(exitPrice, realizedPnl, Position.PositionStatus.STOPPED_OUT);
        
        assertEquals(Position.PositionStatus.STOPPED_OUT, longPosition.getStatus());
        assertEquals(exitPrice, longPosition.getExitPrice(), 0.01);
        assertEquals(realizedPnl, longPosition.getRealizedPnl(), 0.01);
        assertTrue(longPosition.getRealizedPnl() < 0);
    }

    @Test
    @DisplayName("Position at breakeven returns zero P&L")
    void testBreakevenPosition() {
        double currentPrice = longPosition.getEntryPrice();
        
        double unrealizedPnl = longPosition.calculateUnrealizedPnl(currentPrice);
        double unrealizedPnlPercent = longPosition.calculateUnrealizedPnlPercent(currentPrice);
        
        assertEquals(0.0, unrealizedPnl, 0.01);
        assertEquals(0.0, unrealizedPnlPercent, 0.01);
    }

    @Test
    @DisplayName("Position with zero quantity returns zero P&L")
    void testZeroQuantityPosition() {
        Position zeroQtyPosition = Position.builder()
            .id("pos-3")
            .agentId("agent-1")
            .symbol("BTCUSDT")
            .direction(TradeDirection.LONG)
            .entryPrice(50000.0)
            .quantity(0.0)
            .status(Position.PositionStatus.OPEN)
            .openedAt(Instant.now())
            .build();

        double unrealizedPnl = zeroQtyPosition.calculateUnrealizedPnl(52000.0);
        
        assertEquals(0.0, unrealizedPnl, 0.01);
    }
}
