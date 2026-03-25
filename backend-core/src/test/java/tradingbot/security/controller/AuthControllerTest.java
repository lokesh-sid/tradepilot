package tradingbot.security.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import tradingbot.AbstractContainerIntegrationTest;
import tradingbot.agent.infrastructure.llm.LLMProvider;
import tradingbot.security.dto.LoginRequest;
import tradingbot.security.dto.RefreshTokenRequest;
import tradingbot.security.dto.RegisterRequest;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("container-test")
@DisplayName("AuthController Integration Tests (Testcontainers Postgres)")
class AuthControllerTest extends AbstractContainerIntegrationTest {

    @MockitoBean
    private LLMProvider llmProvider;

    @MockitoBean
    @SuppressWarnings("rawtypes")
    private ProxyManager authRateLimitProxyManager;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private BucketProxy bucket;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void stubRateLimiter() {
        RemoteBucketBuilder builder = Mockito.mock(RemoteBucketBuilder.class);
        bucket = Mockito.mock(BucketProxy.class);
        Mockito.when(authRateLimitProxyManager.builder()).thenReturn(builder);
        Mockito.when(builder.build(Mockito.any(), Mockito.<Supplier<BucketConfiguration>>any())).thenReturn(bucket);
        Mockito.when(bucket.tryConsume(Mockito.anyLong())).thenReturn(true);
    }

    // ── Rate limiting ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rate limiting")
    class RateLimiting {

        @Test
        @DisplayName("returns 429 when the bucket is exhausted")
        void returns429WhenRateLimitExceeded() throws Exception {
            Mockito.when(bucket.tryConsume(Mockito.anyLong())).thenReturn(false);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest("anyuser", "anypassword"))))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error").value("too_many_requests"));
        }
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        @Test
        @DisplayName("returns 201 with tokens on valid registration")
        void registersSuccessfully() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "newuser", "newuser@example.com", "Test123!@#", "New User");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.access_token").exists())
                    .andExpect(jsonPath("$.refresh_token").exists())
                    .andExpect(jsonPath("$.token_type").value("Bearer"))
                    .andExpect(jsonPath("$.expires_in").isNumber())
                    .andExpect(jsonPath("$.username").value("newuser"))
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("returns 400 when username is already taken")
        void rejectsDuplicateUsername() throws Exception {
            RegisterRequest request = new RegisterRequest(
                    "dupuser", "dup@example.com", "Test123!@#", "Dup User");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_request"));
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("returns 200 with access and refresh tokens on valid credentials")
        void loginSuccessfully() throws Exception {
            registerUser("loginuser", "login@example.com", "Test123!@#");

            LoginRequest login = new LoginRequest("loginuser", "Test123!@#");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(login)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.access_token").exists())
                    .andExpect(jsonPath("$.refresh_token").exists())
                    .andExpect(jsonPath("$.token_type").value("Bearer"))
                    .andExpect(jsonPath("$.expires_in").isNumber())
                    .andExpect(jsonPath("$.username").value("loginuser"))
                    .andExpect(jsonPath("$.scope").exists())
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("returns 401 with error=invalid_grant on wrong password")
        void rejectsWrongPassword() throws Exception {
            registerUser("wrongpwuser", "wrongpw@example.com", "Test123!@#");

            LoginRequest login = new LoginRequest("wrongpwuser", "WrongPassword!");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(login)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("invalid_grant"));
        }

        @Test
        @DisplayName("returns 401 for a username that does not exist")
        void rejectsUnknownUsername() throws Exception {
            LoginRequest login = new LoginRequest("ghost-user-xyz", "Test123!@#");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(login)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("invalid_grant"));
        }
    }

    // ── Token refresh ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("returns 200 with new token pair on a valid refresh token")
        void refreshesSuccessfully() throws Exception {
            registerUser("refreshuser", "refresh@example.com", "Test123!@#");
            String refreshToken = loginAndGetRefreshToken("refreshuser", "Test123!@#");

            RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.access_token").exists())
                    .andExpect(jsonPath("$.refresh_token").exists())
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("returns 401 with error=invalid_token on a malformed token")
        void rejectsMalformedToken() throws Exception {
            RefreshTokenRequest request = new RefreshTokenRequest("this.is.not.a.valid.jwt");

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("invalid_token"));
        }

        @Test
        @DisplayName("returns 401 when an access token is submitted instead of a refresh token")
        void rejectsAccessTokenAsRefreshToken() throws Exception {
            registerUser("wrongtypeuser", "wrongtype@example.com", "Test123!@#");
            String accessToken = loginAndGetAccessToken("wrongtypeuser", "Test123!@#");

            RefreshTokenRequest request = new RefreshTokenRequest(accessToken);

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("invalid_token"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void registerUser(String username, String email, String password) throws Exception {
        RegisterRequest request = new RegisterRequest(username, email, password, username);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String loginAndGetAccessToken(String username, String password) throws Exception {
        return extractToken(username, password, "access_token");
    }

    private String loginAndGetRefreshToken(String username, String password) throws Exception {
        return extractToken(username, password, "refresh_token");
    }

    private String extractToken(String username, String password, String field) throws Exception {
        LoginRequest login = new LoginRequest(username, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get(field).asText();
    }
}
