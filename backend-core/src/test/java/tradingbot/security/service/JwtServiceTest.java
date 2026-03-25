package tradingbot.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("JwtService")
class JwtServiceTest {

    private static final String VALID_SECRET = "test-only-secret-do-not-use-in-production-x9p3";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 86_400_000L);
        ReflectionTestUtils.setField(jwtService, "issuer", "simple-trading-bot");
    }

    // ── validateSecret ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateSecret()")
    class ValidateSecret {

        @Test
        @DisplayName("accepts a valid secret of sufficient length")
        void acceptsValidSecret() {
            ReflectionTestUtils.setField(jwtService, "jwtSecret", VALID_SECRET);
            // no exception expected
            jwtService.validateSecret();
        }

        @Test
        @DisplayName("rejects null secret")
        void rejectsNullSecret() {
            ReflectionTestUtils.setField(jwtService, "jwtSecret", null);
            assertThatThrownBy(() -> jwtService.validateSecret())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jwt.secret must be set");
        }

        @Test
        @DisplayName("rejects blank secret")
        void rejectsBlankSecret() {
            ReflectionTestUtils.setField(jwtService, "jwtSecret", "   ");
            assertThatThrownBy(() -> jwtService.validateSecret())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("jwt.secret must be set");
        }

        @Test
        @DisplayName("rejects secret shorter than 32 characters")
        void rejectsTooShortSecret() {
            ReflectionTestUtils.setField(jwtService, "jwtSecret", "short");
            assertThatThrownBy(() -> jwtService.validateSecret())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("too short");
        }

        @Test
        @DisplayName("rejects the 'dummy' placeholder secret")
        void rejectsDummyPlaceholder() {
            ReflectionTestUtils.setField(jwtService, "jwtSecret", "dummy");
            assertThatThrownBy(() -> jwtService.validateSecret())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("known placeholder");
        }

        @Test
        @DisplayName("rejects the hardcoded production placeholder secret")
        void rejectsHardcodedProductionPlaceholder() {
            ReflectionTestUtils.setField(jwtService, "jwtSecret",
                    "YourVerySecureSecretKeyThatIsAtLeast256BitsLong1234567890CHANGE_THIS_IN_PRODUCTION");
            assertThatThrownBy(() -> jwtService.validateSecret())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("known placeholder");
        }

        @Test
        @DisplayName("rejects a secret that is exactly 31 characters (boundary)")
        void rejectsBoundaryShortSecret() {
            ReflectionTestUtils.setField(jwtService, "jwtSecret", "a".repeat(31));
            assertThatThrownBy(() -> jwtService.validateSecret())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("too short");
        }

        @Test
        @DisplayName("accepts a secret that is exactly 32 characters (boundary)")
        void acceptsBoundaryValidSecret() {
            ReflectionTestUtils.setField(jwtService, "jwtSecret", "a".repeat(32));
            jwtService.validateSecret(); // no exception
        }
    }

    // ── Token generation and validation ───────────────────────────────────────

    @Nested
    @DisplayName("token generation and validation")
    class TokenGenerationAndValidation {

        @BeforeEach
        void setSecret() {
            ReflectionTestUtils.setField(jwtService, "jwtSecret", VALID_SECRET);
        }

        @Test
        @DisplayName("generates a valid access token with correct claims")
        void generatesAccessToken() {
            String token = jwtService.generateAccessToken("user-1", "alice", Set.of("ROLE_USER"));

            assertThat(jwtService.isTokenValid(token)).isTrue();
            assertThat(jwtService.isAccessToken(token)).isTrue();
            assertThat(jwtService.isRefreshToken(token)).isFalse();
            assertThat(jwtService.extractUserId(token)).isEqualTo("user-1");
            assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
            assertThat(jwtService.extractRoles(token)).containsExactly("ROLE_USER");
        }

        @Test
        @DisplayName("generates a valid refresh token with correct claims")
        void generatesRefreshToken() {
            String token = jwtService.generateRefreshToken("user-1");

            assertThat(jwtService.isTokenValid(token)).isTrue();
            assertThat(jwtService.isRefreshToken(token)).isTrue();
            assertThat(jwtService.isAccessToken(token)).isFalse();
            assertThat(jwtService.extractUserId(token)).isEqualTo("user-1");
        }

        @Test
        @DisplayName("rejects a tampered token as invalid")
        void rejectsTamperedToken() {
            String token = jwtService.generateAccessToken("user-1", "alice", Set.of("ROLE_USER"));
            String tampered = token.substring(0, token.length() - 4) + "xxxx";

            assertThat(jwtService.isTokenValid(tampered)).isFalse();
        }

        @Test
        @DisplayName("rejects a token signed with a different secret")
        void rejectsTokenWithWrongSecret() {
            JwtService other = new JwtService();
            ReflectionTestUtils.setField(other, "jwtSecret", "different-secret-also-long-enough-here12");
            ReflectionTestUtils.setField(other, "accessTokenExpiration", 3_600_000L);
            ReflectionTestUtils.setField(other, "refreshTokenExpiration", 86_400_000L);
            ReflectionTestUtils.setField(other, "issuer", "simple-trading-bot");

            String tokenFromOther = other.generateAccessToken("user-1", "alice", Set.of("ROLE_USER"));

            assertThat(jwtService.isTokenValid(tokenFromOther)).isFalse();
        }

        @Test
        @DisplayName("reports an expired token as invalid")
        void rejectsExpiredToken() {
            JwtService shortLived = new JwtService();
            ReflectionTestUtils.setField(shortLived, "jwtSecret", VALID_SECRET);
            ReflectionTestUtils.setField(shortLived, "accessTokenExpiration", -1000L); // already expired
            ReflectionTestUtils.setField(shortLived, "refreshTokenExpiration", 86_400_000L);
            ReflectionTestUtils.setField(shortLived, "issuer", "simple-trading-bot");

            String expired = shortLived.generateAccessToken("user-1", "alice", Set.of("ROLE_USER"));

            assertThat(jwtService.isTokenValid(expired)).isFalse();
            assertThat(jwtService.isTokenExpired(expired)).isTrue();
        }

        @Test
        @DisplayName("extractUsername returns null for a tampered token without throwing")
        void extractUsernameReturnNullOnInvalidToken() {
            assertThat(jwtService.extractUsername("not.a.jwt")).isNull();
        }

        @Test
        @DisplayName("getAccessTokenExpirationSeconds converts ms to seconds correctly")
        void expirationSecondsConversion() {
            assertThat(jwtService.getAccessTokenExpirationSeconds()).isEqualTo(3600L);
        }
    }
}
