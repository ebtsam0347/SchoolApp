package com.forward.hrms.gateway.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.web.security.JwtAuthenticationFilter;

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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-caller token bucket on {@code GET /bff/search}. In-process map keyed
 * by gateway-validated user id — single-tenant deployment, single gateway
 * pod per node, so no Redis needed. If we ever scale the gateway
 * horizontally, swap the inner state for a Redis Lua script.
 *
 * <p>Capacity = {@code burst}, refill = {@code refillPerSecond} tokens/s.
 * Default 10 req/s, burst 20 — palette is keystroke-driven and bursts
 * during typing. Bucket implementation lives in {@link TokenBucket}
 * (shared with {@link AuthRateLimitFilter} and
 * {@link PerUserRateLimitFilter} after the §6.5 refactor).
 *
 * <p>Anonymous calls (no {@code CurrentUser}, e.g. requests rejected
 * downstream by Spring Security anyway) bypass the bucket — there's
 * nothing useful to key on, and Spring Security will 401 them in the
 * same chain.
 */
@Slf4j
public class SearchRateLimitFilter extends OncePerRequestFilter implements Ordered {

    /** Run after the JWT filter so we have a user id; before route dispatch. */
    public static final int ORDER = JwtAuthenticationFilter.ORDER + 5;

    private static final String PATH = "/bff/search";

    private final SearchRateLimitProperties props;
    /** Instantiated locally — the gateway has no top-level ObjectMapper bean
     *  (no controllers serializing JSON), and the body shape is fixed. */
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<Long, TokenBucket> buckets = new ConcurrentHashMap<>();

    public SearchRateLimitFilter(SearchRateLimitProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        if (!props.enabled()) return true;
        return !PATH.equals(req.getRequestURI()) || !"GET".equalsIgnoreCase(req.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Optional<CurrentUser> user = CurrentUserHolder.get();
        if (user.isEmpty()) {
            chain.doFilter(req, res);
            return;
        }

        TokenBucket bucket = buckets.computeIfAbsent(user.get().userId(),
                id -> new TokenBucket(props.burst(), props.refillPerSecond()));

        long retryAfterMs = bucket.tryConsume();
        if (retryAfterMs > 0) {
            writeProblem(req, res, retryAfterMs);
            return;
        }
        chain.doFilter(req, res);
    }

    private void writeProblem(HttpServletRequest req, HttpServletResponse res, long retryAfterMs)
            throws IOException {
        long retryAfterSec = Math.max(1, (retryAfterMs + 999) / 1000);
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSec));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "Too Many Requests");
        body.put("status", 429);
        body.put("detail", "Palette search rate limit exceeded — retry shortly.");
        body.put("instance", req.getRequestURI());
        body.put("code", "RATE_LIMITED");
        body.put("retryAfterMs", retryAfterMs);

        mapper.writeValue(res.getOutputStream(), body);
    }

    @Override
    public int getOrder() { return ORDER; }
}
