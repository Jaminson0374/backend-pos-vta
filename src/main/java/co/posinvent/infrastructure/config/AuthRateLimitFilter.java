package co.posinvent.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory token-bucket rate limiter for the unauthenticated auth endpoints.
 * One bucket exists per {@code endpoint|client-ip}; buckets are created full.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String FORGOT_PASSWORD_PATH = "/api/v1/auth/forgot-password";
    private static final String SET_PASSWORD_PATH = "/api/v1/auth/set-password";

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long IDLE_WINDOWS_BEFORE_EVICTION = 2;

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, TokenBucket> buckets;

    @Autowired
    public AuthRateLimitFilter(RateLimitProperties properties) {
        // WebMvcTest slices import SecurityConfig without JacksonConfig, so keep the
        // filter self-contained with the same plain mapper the application uses.
        this(properties, new ObjectMapper());
    }

    public AuthRateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.buckets = boundedBuckets(properties.maxBuckets());
    }

    /**
     * Access-order map: reads move an entry to the end, and the eldest entry (least recently
     * used) is dropped once the configured bound is exceeded.
     */
    private static Map<String, TokenBucket> boundedBuckets(int maxBuckets) {
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TokenBucket> eldest) {
                return size() > maxBuckets;
            }
        });
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        var rule = "POST".equalsIgnoreCase(request.getMethod())
                ? ruleFor(request.getRequestURI())
                : null;

        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        var key = request.getRequestURI() + "|" + resolveClientIp(request);

        long retryAfterSeconds;
        synchronized (buckets) {
            var bucket = buckets.computeIfAbsent(key,
                    ignored -> new TokenBucket(rule.capacity(), rule.windowSeconds() * NANOS_PER_SECOND));
            retryAfterSeconds = bucket.tryConsume();
        }

        if (retryAfterSeconds > 0) {
            writeRateLimited(response, retryAfterSeconds);
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Scheduled(fixedDelay = 600_000)
    public void evictIdleBuckets() {
        // Iterating a synchronized map (even through entrySet) requires external locking.
        synchronized (buckets) {
            buckets.entrySet().removeIf(entry -> entry.getValue().canEvict());
        }
    }

    private EndpointRule ruleFor(String uri) {
        return switch (uri) {
            case LOGIN_PATH -> new EndpointRule(
                    properties.loginCapacity(), properties.loginWindowSeconds());
            case FORGOT_PASSWORD_PATH -> new EndpointRule(
                    properties.forgotPasswordCapacity(), properties.forgotPasswordWindowSeconds());
            case SET_PASSWORD_PATH -> new EndpointRule(
                    properties.setPasswordCapacity(), properties.setPasswordWindowSeconds());
            default -> null;
        };
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (!properties.trustForwardedHeaders()) {
            return request.getRemoteAddr();
        }

        var forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null) {
            for (String candidate : forwardedFor.split(",")) {
                String ip = candidate.trim();
                if (isUsableIp(ip)) {
                    return ip;
                }
            }
        }

        var realIp = request.getHeader("X-Real-IP");
        if (realIp != null && isUsableIp(realIp.trim())) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private boolean isUsableIp(String value) {
        return !value.isEmpty() && !"unknown".equalsIgnoreCase(value);
    }

    private void writeRateLimited(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));

        // The shared ErrorResponse record carries an OffsetDateTime, which the application's
        // plain ObjectMapper cannot serialize. Keep the exact same JSON shape with a string
        // timestamp instead of adding a new dependency.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("errorCode", "RATE_LIMITED");
        body.put("message", "Demasiados intentos. Intentá nuevamente más tarde.");
        body.put("details", List.of());

        objectMapper.writeValue(response.getWriter(), body);
    }

    private record EndpointRule(int capacity, int windowSeconds) {}

    private static final class TokenBucket {

        private final int capacity;
        private final long windowNanos;
        private double tokens;
        private long lastRefillNanos;
        private long lastAccessNanos;

        private TokenBucket(int capacity, long windowNanos) {
            this.capacity = capacity;
            this.windowNanos = windowNanos;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
            this.lastAccessNanos = this.lastRefillNanos;
        }

        /**
         * @return 0 when the request is allowed, otherwise the seconds until the next token.
         */
        synchronized long tryConsume() {
            long now = System.nanoTime();
            refill(now);
            lastAccessNanos = now;
            lastRefillNanos = now;

            if (tokens >= 1.0d) {
                tokens -= 1.0d;
                return 0L;
            }

            double missingTokens = 1.0d - tokens;
            long waitNanos = (long) Math.ceil(missingTokens * windowNanos / capacity);
            return Math.max(1L, (waitNanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND);
        }

        synchronized boolean canEvict() {
            long now = System.nanoTime();
            long idleNanos = now - lastAccessNanos;
            if (idleNanos <= IDLE_WINDOWS_BEFORE_EVICTION * windowNanos) {
                return false;
            }
            refill(now);
            return tokens >= capacity;
        }

        private void refill(long now) {
            long elapsed = now - lastRefillNanos;
            if (elapsed > 0) {
                tokens = Math.min(capacity,
                        tokens + ((double) elapsed * capacity / windowNanos));
            }
        }
    }
}
