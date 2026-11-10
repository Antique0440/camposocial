package org.benjamincharles.camposocial.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter protecting authentication endpoints (/login, /signup)
 * against brute-force credential stuffing and registration spam.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final int loginLimit;
    private final int signupLimit;
    private final Map<String, Deque<Long>> requestWindows = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${campo.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${campo.security.rate-limit.login-per-minute:15}") int loginLimit,
            @Value("${campo.security.rate-limit.signup-per-minute:5}") int signupLimit
    ) {
        this.enabled = enabled;
        this.loginLimit = loginLimit;
        this.signupLimit = signupLimit;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method) && ("/login".equals(path) || "/signup".equals(path))) {
            int limit = "/login".equals(path) ? loginLimit : signupLimit;
            String clientIp = extractClientIp(request);
            String bucketKey = clientIp + ":" + path;

            long now = System.currentTimeMillis();
            long windowStart = now - 60_000L; // 1-minute window

            Deque<Long> timestamps = requestWindows.computeIfAbsent(bucketKey, k -> new ArrayDeque<>());
            synchronized (timestamps) {
                // Evict expired entries
                while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                    timestamps.pollFirst();
                }

                if (timestamps.size() >= limit) {
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.setHeader("Retry-After", "60");
                    response.setContentType("text/plain;charset=UTF-8");
                    response.getWriter().write("Too many attempts. Please wait a minute before trying again.");
                    return;
                }

                timestamps.addLast(now);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    /**
     * Resets rate-limiting buckets (useful for test teardown).
     */
    public void reset() {
        requestWindows.clear();
    }
}

