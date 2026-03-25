package tradingbot.agent.api.dto;

import java.time.Instant;

public record PerformanceResponse(
    String agentId,
    int totalTrades,
    int winningTrades,
    int losingTrades,
    double totalPnl,
    double winRate,
    double maxDrawdown,
    double peakCapital,
    double currentCapital,
    double averageWin,
    double averageLoss,
    double sharpeRatio,
    Instant lastUpdated
) {}
