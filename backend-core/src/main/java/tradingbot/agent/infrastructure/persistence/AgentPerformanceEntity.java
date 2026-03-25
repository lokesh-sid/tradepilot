package tradingbot.agent.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_performance")
public class AgentPerformanceEntity {

    @Id
    @Column(name = "agent_id")
    private String agentId;

    @Column(name = "total_trades")
    private int totalTrades = 0;

    @Column(name = "winning_trades")
    private int winningTrades = 0;

    @Column(name = "losing_trades")
    private int losingTrades = 0;

    @Column(name = "total_pnl")
    private double totalPnl = 0.0;

    @Column(name = "win_rate")
    private double winRate = 0.0;

    @Column(name = "max_drawdown")
    private double maxDrawdown = 0.0;

    @Column(name = "peak_capital")
    private double peakCapital = 0.0;

    @Column(name = "current_capital")
    private double currentCapital = 0.0;

    @Column(name = "average_win")
    private double averageWin = 0.0;

    @Column(name = "average_loss")
    private double averageLoss = 0.0;

    @Column(name = "sharpe_ratio")
    private double sharpeRatio = 0.0;

    @Column(name = "last_updated")
    private Instant lastUpdated;

    public AgentPerformanceEntity() {}

    public AgentPerformanceEntity(String agentId, double initialCapital) {
        this.agentId = agentId;
        this.currentCapital = initialCapital;
        this.peakCapital = initialCapital;
        this.lastUpdated = Instant.now();
    }

    // Getters and Setters
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public int getTotalTrades() { return totalTrades; }
    public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }

    public int getWinningTrades() { return winningTrades; }
    public void setWinningTrades(int winningTrades) { this.winningTrades = winningTrades; }

    public int getLosingTrades() { return losingTrades; }
    public void setLosingTrades(int losingTrades) { this.losingTrades = losingTrades; }

    public double getTotalPnl() { return totalPnl; }
    public void setTotalPnl(double totalPnl) { this.totalPnl = totalPnl; }

    public double getWinRate() { return winRate; }
    public void setWinRate(double winRate) { this.winRate = winRate; }

    public double getMaxDrawdown() { return maxDrawdown; }
    public void setMaxDrawdown(double maxDrawdown) { this.maxDrawdown = maxDrawdown; }

    public double getPeakCapital() { return peakCapital; }
    public void setPeakCapital(double peakCapital) { this.peakCapital = peakCapital; }

    public double getCurrentCapital() { return currentCapital; }
    public void setCurrentCapital(double currentCapital) { this.currentCapital = currentCapital; }

    public double getAverageWin() { return averageWin; }
    public void setAverageWin(double averageWin) { this.averageWin = averageWin; }

    public double getAverageLoss() { return averageLoss; }
    public void setAverageLoss(double averageLoss) { this.averageLoss = averageLoss; }

    public double getSharpeRatio() { return sharpeRatio; }
    public void setSharpeRatio(double sharpeRatio) { this.sharpeRatio = sharpeRatio; }

    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
}
