package tradingbot.security.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Optional request body for POST /api/auth/logout.
 *
 * When the client supplies its refresh token, the server revokes the
 * entire token family server-side.  Omitting the body is still valid
 * (logout succeeds but only the client's copy is discarded).
 */
public record LogoutRequest(
        @JsonProperty("refresh_token") String refreshToken
) {}
