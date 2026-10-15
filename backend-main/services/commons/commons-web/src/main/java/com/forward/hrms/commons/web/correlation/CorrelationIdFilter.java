package com.forward.hrms.commons.web.correlation;

import com.forward.hrms.commons.core.context.CorrelationId;
import com.forward.hrms.commons.core.context.RequestContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads or generates {@code X-Correlation-Id}, populates MDC + the
 * thread-local {@link CorrelationId} + the {@link RequestContext} (IP,
 * user agent), echoes the id on the response. Runs first.
 */
public class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String id = req.getHeader(CorrelationId.HEADER);
        if (id == null || id.isBlank()) id = CorrelationId.generate();
        CorrelationId.set(id);
        MDC.put(CorrelationId.MDC_KEY, id);
        res.setHeader(CorrelationId.HEADER, id);

        RequestContext.set(new RequestContext(id, clientIp(req), req.getHeader("User-Agent")));
        try {
            chain.doFilter(req, res);
        } finally {
            CorrelationId.clear();
            RequestContext.clear();
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    private static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        return req.getRemoteAddr();
    }

    @Override
    public int getOrder() { return ORDER; }
}
