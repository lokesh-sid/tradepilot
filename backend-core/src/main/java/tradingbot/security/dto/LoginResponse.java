package tradingbot.security.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth 2.0 / OpenID Connect compliant authentication response
 * 
 * Follows RFC 6749 (OAuth 2.0) standards with snake_case field naming.
 * 
 * Success Response (HTTP 200/201):
 * - access_token: JWT token for API authentication
 * - token_type: Always "Bearer"
 * - expires_in: Token lifetime in seconds
 * - refresh_token: Token for refreshing access token
 * - scope: Space-separated list of granted permissions (e.g., "user admin")
 * - issued_at: Unix timestamp when token was issued
 * - user_id: User's unique identifier
 * - username: User's username
 * 
 * Error Response (HTTP 4xx/5xx):
 * - error: OAuth 2.0 error code (e.g., "invalid_grant", "invalid_token")
 * - error_description: Human-readable error explanation
 * - error_uri: Link to error documentation (optional)
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        // OAuth 2.0 standard fields (RFC 6749 Section 5.1)
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        String scope,  // Space-separated permissions (e.g., "user admin")
        
        // Metadata for token lifecycle tracking
        @JsonProperty("issued_at") Long issuedAt,  // Unix timestamp
        
        // User context (optional - OIDC pattern prefers separate /userinfo endpoint)
        @JsonProperty("user_id") String userId,
        String username,
        
        // RFC 6749 compliant error fields (Section 5.2)
        @JsonProperty("error") String error,  // OAuth 2.0 error code
        @JsonProperty("error_description") String errorDescription,  // Human-readable message
        @JsonProperty("error_uri") String errorUri  // Documentation link
) {
    /**
     * Constructor for successful authentication
     * 
     * @param accessToken JWT token for API authentication
     * @param refreshToken Token for refreshing access token
     * @param expiresIn Token lifetime in seconds
     * @param userId User's unique identifier
     * @param username User's username
     * @param scope Space-separated list of granted permissions
     */
    public LoginResponse(String accessToken, String refreshToken, long expiresIn, 
                        String userId, String username, String scope) {
        this(
            accessToken, 
            "Bearer", 
            expiresIn, 
            refreshToken, 
            scope,
            Instant.now().getEpochSecond(),  // Current timestamp
            userId, 
            username,
            null,  // No error
            null,  // No error description
            null   // No error URI
        );
    }
    
    /**
     * Static factory method for error responses (RFC 6749 compliant)
     * 
     * @param error OAuth 2.0 error code (e.g., "invalid_grant", "invalid_token", "server_error")
     * @param errorDescription Human-readable error explanation
     * @param errorUri Optional link to error documentation
     * @return LoginResponse with error fields populated
     */
    public static LoginResponse error(String error, String errorDescription, String errorUri) {
        return new LoginResponse(
            null,  // No access token
            null,  // No token type
            null,  // No expiration
            null,  // No refresh token
            null,  // No scope
            null,  // No issued timestamp
            null,  // No user ID
            null,  // No username
            error,
            errorDescription,
            errorUri
        );
    }
    
    /**
     * Convenience method for error responses without error_uri
     */
    public static LoginResponse error(String error, String errorDescription) {
        return error(error, errorDescription, null);
    }
    
    /**
     * Check if this response represents a successful authentication.
     * Success is determined by the presence of an access token.
     *
     * @return true if access token is present, false otherwise
     */
    @JsonIgnore
    public boolean isSuccess() {
        return accessToken != null && !accessToken.isEmpty();
    }

    /**
     * Check if this response represents an error.
     * Error is determined by the presence of an error code.
     *
     * @return true if error code is present, false otherwise
     */
    @JsonIgnore
    public boolean isError() {
        return error != null && !error.isEmpty();
    }
}
