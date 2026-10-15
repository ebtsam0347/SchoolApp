package com.forward.hrms.gateway.logging;

import com.forward.hrms.commons.web.security.IdentityHeaders;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Gateway-specific access log line. Logs which downstream URL the request
 * was routed to (or "(local)" if it never matched a route — e.g. swagger,
 * actuator). Runs at the outermost servlet level, after Spring Cloud
 * Gateway has populated {@link MvcUtils#GATEWAY_REQUEST_URL_ATTR}.
 *
 * <p>This sits alongside commons-web's {@code RequestLoggingFilter} —
 * that one logs every service the same way; this one adds the routing
 * decision the gateway uniquely owns.
 */
@Slf4j
public class GatewayRouteLoggingFilter extends OncePerRequestFilter implements Ordered {

    /**
     * One slot before commons-web's RequestLoggingFilter so this filter
     * wraps everything else and observes the final routed URL.
     */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 15;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long startNs = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            Object url = req.getAttribute(MvcUtils.GATEWAY_REQUEST_URL_ATTR);
            Object routeId = req.getAttribute(MvcUtils.GATEWAY_ROUTE_ID_ATTR);
            Object userId = req.getAttribute(IdentityHeaders.REQUEST_ATTR_USER_ID);
            log.info("gateway routed method={} path={} → routeId={} target={} status={} durationMs={} userId={}",
                    req.getMethod(),
                    req.getRequestURI(),
                    routeId != null ? routeId : "(none)",
                    url != null ? url : "(local)",
                    res.getStatus(),
                    durationMs,
                    userId != null ? userId : "-");
        }
    }

    @Override
    public int getOrder() { return ORDER; }
}
