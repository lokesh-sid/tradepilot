package tradingbot.agent.domain.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class OrderIdGenerator {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    public static String forAgent(String agentId) {
        String timestamp = FORMATTER.format(Instant.now());
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        return agentId + "-" + timestamp + "-" + shortUuid;
    }
}
