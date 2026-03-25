package tradingbot.security.filter;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-IP rate limiter for authentication endpoints.
 *
 * <p>Uses Bucket4j backed by Redis to enforce a distributed token-bucket limit
 * per client IP. Because the state lives in Redis, the limit is shared across
 * all application nodes.
 *
 * <p>Rate limit parameters are configured via {@code auth.rate-limit.*} properties.
 * Returns HTTP 429 with an RFC 6749 error body when the limit is exceeded.
 * Requests that do not target the protected paths pass through without overhead.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh"
    );

    private final ProxyManager<String> proxyManager;
    private final BucketConfiguration bucketConfiguration;

    public AuthRateLimitFilter(ProxyManager<String> proxyManager,
                               BucketConfiguration bucketConfiguration) {
        this.proxyManager = proxyManager;
        this.bucketConfiguration = bucketConfiguration;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }

        if (!PROTECTED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String key = "auth-ip-" + clientIp;
        var bucket = proxyManager.builder().build(key, () -> bucketConfiguration);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            logger.warn("Rate limit exceeded for IP {} on {}", clientIp, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"too_many_requests\","
                    + "\"error_description\":\"Too many requests — please slow down and try again later.\"}");
        }
    }

    /**
     * Extracts the real client IP, honouring {@code X-Forwarded-For} when
     * the application sits behind a reverse proxy.
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
