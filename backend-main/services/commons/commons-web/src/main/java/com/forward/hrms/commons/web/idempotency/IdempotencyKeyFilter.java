package com.forward.hrms.commons.web.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Dedupes mutation requests by {@code Idempotency-Key} header against Redis
 * (24 h TTL) and replays the cached response on retry. Only kicks in for
 * POST/PUT/PATCH/DELETE.
 *
 * <p>If two requests with the same key arrive concurrently, the second one
 * gets {@code 409 IDEMPOTENCY_REPLAY} immediately rather than blocking.
 * The cached payload contains status + body; headers other than
 * Content-Type are not replayed.
 */
@Slf4j
@RequiredArgsConstructor
public class IdempotencyKeyFilter extends OncePerRequestFilter implements Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 40;
    public static final String HEADER = "Idempotency-Key";

    private static final String NS = "idem:";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        if (!isMutation(req.getMethod())) { chain.doFilter(req, res); return; }
        String key = req.getHeader(HEADER);
        if (key == null || key.isBlank()) { chain.doFilter(req, res); return; }

        String redisKey = NS + req.getRequestURI() + ":" + key;
        Boolean reserved = redis.opsForValue().setIfAbsent(redisKey, IN_PROGRESS, TTL);

        if (Boolean.FALSE.equals(reserved)) {
            String cached = redis.opsForValue().get(redisKey);
            if (cached == null || IN_PROGRESS.equals(cached)) {
                writeReplayConflict(res);
                return;
            }
            replay(cached, res);
            return;
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(res);
        try {
            chain.doFilter(req, wrapped);
        } finally {
            try {
                String body = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8);
                String contentType = wrapped.getContentType();
                String payload = json.writeValueAsString(Map.of(
                        "status", wrapped.getStatus(),
                        "contentType", contentType == null ? "" : contentType,
                        "body", body));
                redis.opsForValue().set(redisKey, payload, TTL);
            } catch (Exception e) {
                log.warn("Failed to cache idempotent response, releasing key {}", redisKey, e);
                redis.delete(redisKey);
            }
            wrapped.copyBodyToResponse();
        }
    }

    private static boolean isMutation(String m) {
        return "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m) || "DELETE".equals(m);
    }

    private void replay(String payload, HttpServletResponse res) throws IOException {
        try {
            Map<?, ?> m = json.readValue(payload, Map.class);
            res.setStatus(((Number) m.get("status")).intValue());
            String ct = String.valueOf(m.get("contentType"));
            if (!ct.isBlank() && !"null".equals(ct)) res.setContentType(ct);
            res.setHeader("X-Idempotent-Replay", "true");
            res.getWriter().write(String.valueOf(m.get("body")));
        } catch (JsonProcessingException e) {
            writeReplayConflict(res);
        }
    }

    private static void writeReplayConflict(HttpServletResponse res) throws IOException {
        res.setStatus(409);
        res.setContentType("application/problem+json");
        res.getWriter().write("{\"code\":\"IDEMPOTENCY_REPLAY\",\"detail\":\"Request with this Idempotency-Key is in progress.\"}");
    }

    @Override
    public int getOrder() { return ORDER; }
}
