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
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * §6.5 — generic per-user rate limiter (refactored from
 * {@code SearchRateLimitFilter}). Applied to noisy authenticated endpoints
 * that don't deserve a per-endpoint bespoke filter:
 * {@code /audit/search}, {@code /notifications/**}.
 *
 * <p>Buckets are keyed by {@code (path-prefix, userId)} so a user's audit
 * search bursts don't starve their notification reads. In-process map;
 * single-tenant single-pod deployment doesn't need Redis.
 *
 * <p>Path matching uses {@link AntPathMatcher} so {@code /notifications/**}
 * catches every sub-route.
 */
@Slf4j
public class PerUserRateLimitFilter extends OncePerRequestFilter implements Ordered {

    /** Run after the JWT filter so {@link CurrentUser} is populated. */
    public static final int ORDER = JwtAuthenticationFilter.ORDER + 4;

    private final RateLimitProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AntPathMatcher matcher = new AntPathMatcher();

    public PerUserRateLimitFilter(RateLimitProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        if (!props.perUser().enabled()) return true;
        return matchedPath(req.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Optional<CurrentUser> user = CurrentUserHolder.get();
        if (user.isEmpty()) {
            chain.doFilter(req, res);
            return;
        }
        String matched = matchedPath(req.getRequestURI());
        // shouldNotFilter already guarantees non-null but be defensive.
        if (matched == null) {
            chain.doFilter(req, res);
            return;
        }
        String key = matched + "|" + user.get().userId();
        TokenBucket bucket = buckets.computeIfAbsent(key,
                k -> new TokenBucket(props.perUser().burst(), props.perUser().refillPerSecond()));
        long wait = bucket.tryConsume();
        if (wait > 0) {
            writeProblem(req, res, wait);
            return;
        }
        chain.doFilter(req, res);
    }

    /** Returns the first matching pattern, or null if no pattern matches. */
    private String matchedPath(String uri) {
        List<String> paths = props.perUser().paths();
        if (paths == null) return null;
        for (String pattern : paths) {
            if (matcher.match(pattern, uri)) return pattern;
        }
        return null;
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
        body.put("detail", "Per-user rate limit exceeded — retry shortly.");
        body.put("instance", req.getRequestURI());
        body.put("code", "RATE_LIMITED");
        body.put("retryAfterMs", wait);
        mapper.writeValue(res.getOutputStream(), body);
    }

    @Override
    public int getOrder() { return ORDER; }
}
