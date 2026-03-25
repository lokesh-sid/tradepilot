package tradingbot.security.dto;

/**
 * Response DTO for logout operation
 * 
 * Confirms that the logout request was processed successfully.
 * Client should delete tokens locally after receiving this response.
 */
public record LogoutResponse(
        String message
) {
    /**
     * Default logout response
     */
    public static LogoutResponse success() {
        return new LogoutResponse("Logged out successfully");
    }
}
