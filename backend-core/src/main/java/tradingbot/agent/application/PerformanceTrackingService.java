package tradingbot.agent.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tradingbot.agent.api.dto.PerformanceMapper;
import tradingbot.agent.api.dto.PerformanceResponse;
import tradingbot.agent.domain.execution.ExecutionResult;
import tradingbot.agent.domain.model.Agent;
import tradingbot.agent.domain.model.AgentId;
import tradingbot.agent.domain.repository.AgentRepository;
import tradingbot.agent.infrastructure.persistence.AgentPerformanceEntity;
import tradingbot.agent.infrastructure.repository.AgentPerformanceRepository;

@Service
public class PerformanceTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceTrackingService.class);

    private final AgentPerformanceRepository performanceRepository;
    private final AgentRepository agentRepository;
    private final PerformanceMapper performanceMapper;

    public PerformanceTrackingService(
            AgentPerformanceRepository performanceRepository, 
            AgentRepository agentRepository,
            PerformanceMapper performanceMapper) {
        this.performanceRepository = performanceRepository;
        this.agentRepository = agentRepository;
        this.performanceMapper = performanceMapper;
    }

    @Transactional
    public void recordExecution(String agentIdStr, ExecutionResult result) {
        if (!result.success() || result.action() == ExecutionResult.ExecutionAction.NOOP) {
            return; // Only track successful fills
        }

        AgentId agentId = new AgentId(agentIdStr);
        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            logger.warn("Agent {} not found for performance tracking.", agentIdStr);
            return;
        }

        AgentPerformanceEntity perf = performanceRepository.findById(agentIdStr)
                .orElseGet(() -> new AgentPerformanceEntity(agentIdStr, agent.getCapital()));

        // We consider an EXIT action as a closed trade for PNL tracking
        if (result.action() == ExecutionResult.ExecutionAction.EXIT_LONG || 
            result.action() == ExecutionResult.ExecutionAction.EXIT_SHORT) {
            
            perf.setTotalTrades(perf.getTotalTrades() + 1);
            
            double realizedPnl = result.realizedPnl();
            perf.setTotalPnl(perf.getTotalPnl() + realizedPnl);
            perf.setCurrentCapital(perf.getCurrentCapital() + realizedPnl);

            if (realizedPnl > 0) {
                perf.setWinningTrades(perf.getWinningTrades() + 1);
                
                // Update average win
                int w = perf.getWinningTrades();
                perf.setAverageWin(((perf.getAverageWin() * (w - 1)) + realizedPnl) / w);
            } else if (realizedPnl < 0) {
                perf.setLosingTrades(perf.getLosingTrades() + 1);
                
                // Update average loss
                int l = perf.getLosingTrades();
                perf.setAverageLoss(((perf.getAverageLoss() * (l - 1)) + Math.abs(realizedPnl)) / l);
            }

            // Update Peak Capital and Max Drawdown
            if (perf.getCurrentCapital() > perf.getPeakCapital()) {
                perf.setPeakCapital(perf.getCurrentCapital());
            } else {
                double drawdown = (perf.getPeakCapital() - perf.getCurrentCapital()) / perf.getPeakCapital() * 100.0;
                if (drawdown > perf.getMaxDrawdown()) {
                    perf.setMaxDrawdown(drawdown);
                }
            }

            // Update Win Rate
            if (perf.getTotalTrades() > 0) {
                perf.setWinRate((double) perf.getWinningTrades() / perf.getTotalTrades() * 100.0);
            }
            
            // Simple Sharpe Ratio placeholder (assumes risk-free rate is 0, using PnL scale instead of returns)
            // A more complex calculation would require tracking the series of returns over time.
            if (perf.getAverageLoss() > 0) {
                perf.setSharpeRatio((perf.getAverageWin() * perf.getWinRate() / 100.0) / perf.getAverageLoss());
            }

            perf.setLastUpdated(Instant.now());
            performanceRepository.save(perf);
            
            logger.info("Updated performance for agent {}: PNL={}, TotalTrades={}, WinRate={}%",
                    agentId, perf.getTotalPnl(), perf.getTotalTrades(), perf.getWinRate());
        }
    }

    @Transactional(readOnly = true)
    public PerformanceResponse getPerformance(String agentIdStr) {
        AgentId agentId = new AgentId(agentIdStr);
        Agent agent = agentRepository.findById(agentId)
            .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentIdStr));

        AgentPerformanceEntity perf = performanceRepository.findById(agentIdStr)
            .orElseGet(() -> new AgentPerformanceEntity(agentIdStr, agent.getCapital()));

        return performanceMapper.toResponse(perf);
    }
}
