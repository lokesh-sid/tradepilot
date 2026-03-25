package tradingbot.bot.strategy.exit;

import tradingbot.bot.strategy.tracker.TrailingStopTracker;

public class TrailingStopExit implements PositionExitCondition {
    private TrailingStopTracker tracker;

    public TrailingStopExit(TrailingStopTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public boolean shouldExit() {
        return tracker.checkTrailingStop(tracker.getCurrentPrice());
    }
}