package tradingbot;

import java.util.concurrent.ThreadLocalRandom;

public final class TestIds {

    private TestIds() {
    }

    public static long randomNumericId() {
        long min = 100_000_000_000_000L;
        long max = 999_999_999_999_999L;
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    public static String randomNumericIdAsString() {
        return Long.toString(randomNumericId());
    }
}
