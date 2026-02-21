package ro.mateistanescu.matquizspringbootbackend.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies a global fixed-window equivalent rate limit using token bucket semantics:
 * each client IP can execute up to 60 requests per minute across the API.
 */
@Component
public class GlobalRateLimitFilter extends OncePerRequestFilter {
    private static final long GLOBAL_TOKENS_PER_MINUTE = 60L;
    private static final long LOGIN_TOKENS_PER_MINUTE = 10L;
    private static final long REGISTER_TOKENS_PER_MINUTE = 3L;

    private final Map<String, Bucket> globalBucketsByIp = new ConcurrentHashMap<>();
    private final Map<String, Bucket> loginBucketsByIp = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBucketsByIp = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {
        String clientIp = resolveClientIp(request);
        Long scopedLimit = null;
        Long scopedRemaining = null;

        if (isLoginRequest(request)) {
            Bucket loginBucket = loginBucketsByIp.computeIfAbsent(clientIp, key -> createBucket(LOGIN_TOKENS_PER_MINUTE));
            ConsumptionProbe loginProbe = loginBucket.tryConsumeAndReturnRemaining(1);
            if (!loginProbe.isConsumed()) {
                reject(response, LOGIN_TOKENS_PER_MINUTE, loginProbe);
                return;
            }
            scopedLimit = LOGIN_TOKENS_PER_MINUTE;
            scopedRemaining = loginProbe.getRemainingTokens();
        }

        if (isRegisterRequest(request)) {
            Bucket registerBucket = registerBucketsByIp.computeIfAbsent(clientIp, key -> createBucket(REGISTER_TOKENS_PER_MINUTE));
            ConsumptionProbe registerProbe = registerBucket.tryConsumeAndReturnRemaining(1);
            if (!registerProbe.isConsumed()) {
                reject(response, REGISTER_TOKENS_PER_MINUTE, registerProbe);
                return;
            }
            scopedLimit = REGISTER_TOKENS_PER_MINUTE;
            scopedRemaining = registerProbe.getRemainingTokens();
        }

        Bucket globalBucket = globalBucketsByIp.computeIfAbsent(clientIp, key -> createBucket(GLOBAL_TOKENS_PER_MINUTE));
        ConsumptionProbe globalProbe = globalBucket.tryConsumeAndReturnRemaining(1);
        if (globalProbe.isConsumed()) {
            long responseLimit = scopedLimit != null ? scopedLimit : GLOBAL_TOKENS_PER_MINUTE;
            long responseRemaining = scopedRemaining != null ? scopedRemaining : globalProbe.getRemainingTokens();
            response.setHeader("RateLimit-Limit", String.valueOf(responseLimit));
            response.setHeader("RateLimit-Remaining", String.valueOf(responseRemaining));
            filterChain.doFilter(request, response);
            return;
        }

        reject(response, GLOBAL_TOKENS_PER_MINUTE, globalProbe);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private Bucket createBucket(long tokensPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(tokensPerMinute)
                .refillGreedy(tokensPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) &&
                "/api/auth/login".equals(request.getRequestURI());
    }

    private boolean isRegisterRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) &&
                "/api/auth/register".equals(request.getRequestURI());
    }

    private void reject(HttpServletResponse response, long limit, ConsumptionProbe probe) throws IOException {
        long secondsToWait = Math.max(1L, (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader("RateLimit-Limit", String.valueOf(limit));
        response.setHeader("RateLimit-Remaining", "0");
        response.setHeader("RateLimit-Reset", String.valueOf(secondsToWait));
        response.setHeader("Retry-After", String.valueOf(secondsToWait));
        response.getWriter().write("{\"message\":\"Too many requests. Please try again later.\"}");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] candidates = forwardedFor.split(",");
            if (candidates.length > 0) {
                return candidates[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
