package tradingbot.agent.domain.util;

public final class Ids {

    private Ids() {
    }

    public static long requireId(String rawId, String fieldName) {
        if (rawId == null || rawId.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        try {
            return Long.parseLong(rawId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a numeric ID: " + rawId, e);
        }
    }

    public static Long optionalId(String rawId, String fieldName) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        return requireId(rawId, fieldName);
    }

    public static String asString(long id) {
        return Long.toString(id);
    }

    public static String asString(Long id) {
        return id == null ? null : Long.toString(id);
    }
}
