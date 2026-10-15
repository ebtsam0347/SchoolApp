package com.forward.hrms.gateway.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * §6.4 — per-IP rate limit on the unauthenticated auth endpoints
 * ({@code /auth/login}, {@code /auth/refresh}, {@code /auth/logout}).
 * Closes the credential-stuffing-from-one-IP gap that
 * {@code LoginFailureCounter} (per-username lockout) doesn't cover.
 *
 * <p>Keyed on the gateway-trusted client IP (set by
 * {@link com.forward.hrms.gateway.security.ClientInfoForwardingFilter}
 * upstream). Default 30 req/min/IP, burst 30 — generous for a noisy
 * NAT, tight for a brute-forcer.
 *
 * <p>Anonymous endpoints — runs BEFORE the JWT filter so an unauthed
 * caller is rate-limited regardless. {@link Ordered#HIGHEST_PRECEDENCE}
 * + 100 to land before everything except the X-Forwarded-* setter.
 */
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter implements Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private static final Set<String> PATHS = Set.of(
            "/auth/login", "/auth/refresh", "/auth/logout");

    private final RateLimitProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(RateLimitProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        if (!props.auth().enabled()) return true;
        return !"POST".equalsIgnoreCase(req.getMethod()) || !PATHS.contains(req.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String ip = req.getRemoteAddr();
        if (ip == null || ip.isBlank()) {
            // Couldn't key — let the request through; Spring Security still 401s
            // on bad creds.
            chain.doFilter(req, res);
            return;
        }
        TokenBucket bucket = buckets.computeIfAbsent(ip,
                k -> new TokenBucket(props.auth().burst(), props.auth().refillPerMinute() / 60.0));
        long wait = bucket.tryConsume();
        if (wait > 0) {
            writeProblem(req, res, wait);
            return;
        }
        chain.doFilter(req, res);
    }

    private void writeProblem(HttpServletRequest req, HttpServletResponse res, long wait) throws IOException {
        long retryAfterSec = Math.max(1, (wait + 999) / 1000);
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSec));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "Too Many Requests");
        body.put("status", 429);
        body.put("detail", "Auth endpoint rate limit exceeded — retry shortly.");
        body.put("instance", req.getRequestURI());
        body.put("code", "AUTH_RATE_LIMITED");
        body.put("retryAfterMs", wait);
        mapper.writeValue(res.getOutputStream(), body);
    }

    @Override
    public int getOrder() { return ORDER; }
}
