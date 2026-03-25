package tradingbot.agent.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import tradingbot.agent.domain.model.Position;
import tradingbot.agent.domain.model.TradeDirection;
import tradingbot.agent.infrastructure.persistence.PositionEntity;
import tradingbot.agent.infrastructure.repository.PositionRepository;
import tradingbot.bot.service.FuturesExchangeService;

/**
 * PositionMonitoringService - Monitors open positions and tracks P&L
 * 
 * This service:
 * 1. Periodically checks all open positions (every 10 seconds)
 * 2. Calculates unrealized P&L for each position
 * 3. Detects when positions are closed
 * 4. Updates position status and realized P&L
 * 5. Triggers RAG updates with trade outcomes
 */
@Service
public class PositionMonitoringService {
    
    private static final Logger logger = LoggerFactory.getLogger(PositionMonitoringService.class);
    
    private final PositionRepository positionRepository;
    private final FuturesExchangeService exchangeService;
    
    public PositionMonitoringService(
            PositionRepository positionRepository,
            FuturesExchangeService exchangeService) {
        this.positionRepository = positionRepository;
        this.exchangeService = exchangeService;
    }
    
    /**
     * Monitor all open positions every 10 seconds
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 5000)
    public void monitorOpenPositions() {
        try {
            List<PositionEntity> openPositions = positionRepository.findByStatus(PositionEntity.Status.OPEN);
            
            if (openPositions.isEmpty()) {
                return; // No positions to monitor
            }
            
            logger.info("Monitoring {} open position(s)", openPositions.size());
            
            for (PositionEntity positionEntity : openPositions) {
                try {
                    monitorPosition(positionEntity);
                } catch (Exception e) {
                    logger.error("Error monitoring position {}: {}", 
                        positionEntity.getId(), e.getMessage());
                    // Continue monitoring other positions
                }
            }
            
        } catch (Exception e) {
            logger.error("Error in monitorOpenPositions: {}", e.getMessage());
        }
    }
    
    /**
     * Monitor a single position
     */
    private void monitorPosition(PositionEntity positionEntity) {
        String symbol = positionEntity.getSymbol();
        
        try {
            // Fetch current price
            double currentPrice = exchangeService.getCurrentPrice(symbol);
            if (currentPrice <= 0) {
                logger.warn("Invalid price received for symbol {}", symbol);
                return;
            }
            
            // Calculate unrealized P&L
            double unrealizedPnl = calculateUnrealizedPnl(
                positionEntity.getDirection(),
                positionEntity.getEntryPrice(),
                currentPrice,
                positionEntity.getQuantity()
            );
            
            double unrealizedPnlPercent = (unrealizedPnl / (positionEntity.getEntryPrice() * positionEntity.getQuantity())) * 100.0;
            
            // Log position status
            logger.info("Position {} | {} {} | Entry: {} | Current: {} | Unrealized P&L: ${} ({}%)",
                positionEntity.getId(),
                positionEntity.getDirection(),
                symbol,
                String.format("%.2f", positionEntity.getEntryPrice()),
                String.format("%.2f", currentPrice),
                String.format("%.2f", unrealizedPnl),
                String.format("%.2f", unrealizedPnlPercent)
            );
            
            // Update last checked time and P&L
            positionEntity.setLastCheckedAt(Instant.now());
            positionEntity.setLastUnrealizedPnl(unrealizedPnl);
            if (positionEntity.getId() != null) {
                positionRepository.save(positionEntity);
            }
            
            // Check for stop-loss or take-profit hits (manual trigger if needed)
            checkStopLossAndTakeProfit(positionEntity, currentPrice);
            
        } catch (Exception e) {
            logger.error("Error monitoring position {} on {}: {}", 
                positionEntity.getId(), symbol, e.getMessage());
        }
    }
    
    /**
     * Calculate unrealized P&L for a position
     */
    private double calculateUnrealizedPnl(
            PositionEntity.Direction direction,
            double entryPrice,
            double currentPrice,
            double quantity) {
        
        if (direction == PositionEntity.Direction.LONG) {
            return (currentPrice - entryPrice) * quantity;
        } else { // SHORT
            return (entryPrice - currentPrice) * quantity;
        }
    }
    
    /**
     * Check if stop-loss or take-profit should be triggered
     * This is a backup in case exchange orders don't trigger automatically
     */
    private void checkStopLossAndTakeProfit(PositionEntity positionEntity, double currentPrice) {
        boolean shouldClose = false;
        Position.PositionStatus closeReason = Position.PositionStatus.CLOSED;
        
        // Check stop-loss
        if (positionEntity.getStopLoss() != null) {
            if (positionEntity.getDirection() == PositionEntity.Direction.LONG) {
                if (currentPrice <= positionEntity.getStopLoss()) {
                    logger.warn("Stop-loss triggered for {} at {} (stop-loss: {})",
                        positionEntity.getSymbol(), currentPrice, positionEntity.getStopLoss());
                    shouldClose = true;
                    closeReason = Position.PositionStatus.STOPPED_OUT;
                }
            } else { // SHORT
                if (currentPrice >= positionEntity.getStopLoss()) {
                    logger.warn("Stop-loss triggered for {} SHORT at {} (stop-loss: {})",
                        positionEntity.getSymbol(), currentPrice, positionEntity.getStopLoss());
                    shouldClose = true;
                    closeReason = Position.PositionStatus.STOPPED_OUT;
                }
            }
        }
        
        // Check take-profit
        if (positionEntity.getTakeProfit() != null) {
            if (positionEntity.getDirection() == PositionEntity.Direction.LONG) {
                if (currentPrice >= positionEntity.getTakeProfit()) {
                    logger.info("Take-profit triggered for {} at {} (take-profit: {})",
                        positionEntity.getSymbol(), currentPrice, positionEntity.getTakeProfit());
                    shouldClose = true;
                    closeReason = Position.PositionStatus.CLOSED;
                }
            } else { // SHORT
                if (currentPrice <= positionEntity.getTakeProfit()) {
                    logger.info("Take-profit triggered for {} SHORT at {} (take-profit: {})",
                        positionEntity.getSymbol(), currentPrice, positionEntity.getTakeProfit());
                    shouldClose = true;
                    closeReason = Position.PositionStatus.CLOSED;
                }
            }
        }
        
        // Execute emergency exit if needed
        if (shouldClose) {
            executeEmergencyExit(positionEntity, currentPrice, closeReason);
        }
    }
    
    /**
     * Execute emergency exit for a position
     * This is called when stop-loss/take-profit needs manual triggering
     */
    private void executeEmergencyExit(
            PositionEntity positionEntity, 
            double currentPrice, 
            Position.PositionStatus reason) {
        
        try {
            logger.info("Executing emergency exit for position {}: {}",
                positionEntity.getId(), reason);
            
            // Place market order to close position
            String symbol = positionEntity.getSymbol();
            double quantity = positionEntity.getQuantity();
            
          if (positionEntity.getDirection() == PositionEntity.Direction.LONG) {
                exchangeService.exitLongPosition(symbol, quantity);
            } else {
                exchangeService.exitShortPosition(symbol, quantity);
            }
            
            // Calculate realized P&L
            double realizedPnl = calculateUnrealizedPnl(
                positionEntity.getDirection(),
                positionEntity.getEntryPrice(),
                currentPrice,
                quantity
            );
            
            // Update position status
            positionEntity.setStatus(mapPositionStatus(reason));
            positionEntity.setExitPrice(currentPrice);
            positionEntity.setRealizedPnl(realizedPnl);
            positionEntity.setClosedAt(Instant.now());
            positionRepository.save(positionEntity);
            
            logger.info("Position {} closed: Realized P&L = ${}",
                positionEntity.getId(), String.format("%.2f", realizedPnl));
            
        } catch (Exception e) {
            logger.error("Failed to execute emergency exit for position {}: {}",
                positionEntity.getId(), e.getMessage());
        }
    }
    
    /**
     * Create a new position record after order execution
     */
    public Position createPosition(
            String agentId,
            String symbol,
            TradeDirection direction,
            double entryPrice,
            double quantity,
            Double stopLoss,
            Double takeProfit,
            String mainOrderId) {
        
        Position position = Position.builder()
            .id(UUID.randomUUID().toString())
            .agentId(agentId)
            .symbol(symbol)
            .direction(direction)
            .entryPrice(entryPrice)
            .quantity(quantity)
            .stopLoss(stopLoss)
            .takeProfit(takeProfit)
            .mainOrderId(mainOrderId)
            .status(Position.PositionStatus.OPEN)
            .openedAt(Instant.now())
            .build();
        
        // Persist to database
        PositionEntity entity = new PositionEntity(
            position.getId(),
            position.getAgentId(),
            position.getSymbol(),
            PositionEntity.Direction.valueOf(position.getDirection().name()),
            position.getEntryPrice(),
            position.getQuantity(),
            position.getStopLoss(),
            position.getTakeProfit(),
            position.getMainOrderId(),
            PositionEntity.Status.OPEN,
            position.getOpenedAt()
        );
        
        positionRepository.save(entity);
        
        logger.info("Created position: {}", position);
        return position;
    }
    
    /**
     * Get all open positions for an agent
     */
    public List<PositionEntity> getOpenPositions(String agentId) {
        return positionRepository.findByAgentIdAndStatus(agentId, PositionEntity.Status.OPEN);
    }
    
    /**
     * Map Position.PositionStatus to PositionEntity.Status
     */
    private PositionEntity.Status mapPositionStatus(Position.PositionStatus status) {
        return switch (status) {
            case OPEN -> PositionEntity.Status.OPEN;
            case CLOSED -> PositionEntity.Status.CLOSED;
            case STOPPED_OUT -> PositionEntity.Status.STOPPED_OUT;
            case LIQUIDATED -> PositionEntity.Status.LIQUIDATED;
        };
    }
}
