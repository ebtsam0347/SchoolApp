package com.forward.hrms.commons.web.logging;

import com.forward.hrms.commons.web.security.IdentityHeaders;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * One log line per request, structured fields, runs after the correlation
 * filter so MDC has the id. Body / headers intentionally omitted — those
 * belong in trace, not logs.
 *
 * <p>The user id comes from the {@link IdentityHeaders#REQUEST_ATTR_USER_ID}
 * request attribute (set by the JWT or header-identity filter) — not from
 * {@code CurrentUserHolder}, because by the time this filter logs in its
 * finally{} the security chain has already cleared the thread-local.
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter implements Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long startNs = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            Object id = req.getAttribute(IdentityHeaders.REQUEST_ATTR_USER_ID);
            log.info("method={} path={} status={} durationMs={} userId={}",
                    req.getMethod(),
                    req.getRequestURI(),
                    res.getStatus(),
                    durationMs,
                    id != null ? id : "-");
        }
    }

    @Override
    public int getOrder() { return ORDER; }
}
