package tradingbot.bot.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tradingbot.bot.events.TradeSignalEvent;
import tradingbot.bot.messaging.TradeExecutionService.RiskAssessment;
import tradingbot.bot.service.FuturesExchangeService;

/**
 * Risk assessment service for evaluating trade signals before execution.
 * This service helps prevent risky trades and implements risk management rules.
 */
@Service
public class RiskAssessmentService {
    
    private static final Logger log = LoggerFactory.getLogger(RiskAssessmentService.class);
    
    private final FuturesExchangeService exchangeService;
    
    // Risk thresholds
    private static final double MIN_SIGNAL_STRENGTH = 0.3;
    private static final double MAX_SINGLE_TRADE_RISK = 0.02; // 2% of balance
    private static final double MIN_BALANCE_THRESHOLD = 100.0; // Minimum balance to trade
    
    public RiskAssessmentService(FuturesExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }
    
    /**
     * Assesses the risk of executing a trade signal.
     * 
     * @param signalEvent The trade signal to assess
     * @return Risk assessment result
     */
    public RiskAssessment assessTradeSignal(TradeSignalEvent signalEvent) {
        try {
            log.debug("Assessing risk for trade signal: {} - {} {}", 
                signalEvent.getEventId(), signalEvent.getSignal(), signalEvent.getSymbol());
            
            // Check signal strength
            if (signalEvent.getStrength() < MIN_SIGNAL_STRENGTH) {
                return new TradeExecutionService.RiskAssessment(
                    false, 
                    "Signal strength too low: " + signalEvent.getStrength(),
                    0.0
                );
            }
            
            // Check available balance
            double balance = exchangeService.getMarginBalance();
            if (balance < MIN_BALANCE_THRESHOLD) {
                return new TradeExecutionService.RiskAssessment(
                    false,
                    "Insufficient balance: " + balance,
                    0.0
                );
            }
            
            // Calculate risk percentage based on signal strength and market conditions
            double riskPercentage = calculateRiskPercentage(signalEvent, balance);
            
            // Verify risk is within acceptable limits
            if (riskPercentage > MAX_SINGLE_TRADE_RISK) {
                return new TradeExecutionService.RiskAssessment(
                    false,
                    "Risk percentage too high: " + riskPercentage,
                    MAX_SINGLE_TRADE_RISK
                );
            }
            
            // Additional checks could be added here:
            // - Market volatility assessment
            // - Correlation with existing positions
            // - News/event risk
            // - Technical indicator confirmation
            
            log.debug("Risk assessment approved for signal: {} - Risk: {}%", 
                signalEvent.getEventId(), riskPercentage * 100);
            
            return new RiskAssessment(true, null, riskPercentage);
            
        } catch (Exception ex) {
            log.error("Risk assessment failed for signal: {}", signalEvent.getEventId(), ex);
            return new TradeExecutionService.RiskAssessment(
                false,
                "Risk assessment error: " + ex.getMessage(),
                0.0
            );
        }
    }
    
    /**
     * Calculates the appropriate risk percentage for a trade signal.
     * 
     * @param signalEvent The trade signal
     * @param balance Current account balance
     * @return Risk percentage (0.0 to 1.0)
     */
    private double calculateRiskPercentage(TradeSignalEvent signalEvent, double balance) {
        // Base risk calculation
        double baseRisk = MAX_SINGLE_TRADE_RISK * 0.5; // Start with 50% of max risk
        
        // Adjust based on signal strength
        double strengthMultiplier = Math.min(1.0, signalEvent.getStrength() / 0.8); // Scale to 0.8 as full strength
        
        // Adjust based on indicator confidence if available
        double indicatorConfidence = extractIndicatorConfidence(signalEvent);
        double confidenceMultiplier = Math.min(1.0, indicatorConfidence);
        
        // Calculate final risk
        double finalRisk = baseRisk * strengthMultiplier * confidenceMultiplier;
        
        // Ensure within bounds
        return Math.clamp(finalRisk, 0.001, MAX_SINGLE_TRADE_RISK);
    }
    
    /**
     * Extracts indicator confidence from the signal metadata.
     * 
     * @param signalEvent The trade signal
     * @return Confidence level (0.0 to 1.0)
     */
    private double extractIndicatorConfidence(TradeSignalEvent signalEvent) {
        try {
            if (signalEvent.getMetadata() != null) {
                Object confidence = signalEvent.getMetadata().get("confidence");
                if (confidence instanceof String c) {
                    return switch (c.toUpperCase()) {
                        case "HIGH" -> 1.0;
                        case "MEDIUM" -> 0.7;
                        case "LOW" -> 0.4;
                        default -> 0.5;
                    };
                } else if (confidence instanceof Number n) {
                    return Math.clamp(n.doubleValue(), 0.0, 1.0);
                }
            }
            return 0.5; // Default confidence
        } catch (Exception ex) {
            log.debug("Could not extract indicator confidence, using default", ex);
            return 0.5;
        }
    }
}