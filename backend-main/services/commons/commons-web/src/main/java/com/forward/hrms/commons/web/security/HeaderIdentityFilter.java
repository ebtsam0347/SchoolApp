package com.forward.hrms.commons.web.security;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.Ordered;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Reads identity from trusted {@code X-User-*} headers (set by the
 * gateway after it validated the caller's JWT) and populates Spring's
 * {@code SecurityContext} + {@link CurrentUserHolder}.
 *
 * <p>This filter is the downstream-service counterpart to the gateway's
 * JWT validation. Services NEVER parse a JWT themselves — the gateway
 * is the single trust boundary. See {@link IdentityHeaders}.
 */
@Slf4j
public class HeaderIdentityFilter extends OncePerRequestFilter implements Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 30;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String userIdHeader = req.getHeader(IdentityHeaders.USER_ID);
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                CurrentUser user = new CurrentUser(
                        Long.parseLong(userIdHeader),
                        nullable(req.getHeader(IdentityHeaders.USERNAME)),
                        nullable(req.getHeader(IdentityHeaders.FULL_NAME)),
                        parseLong(req.getHeader(IdentityHeaders.SESSION_ID)),
                        parseSet(req.getHeader(IdentityHeaders.ROLES)),
                        parseSet(req.getHeader(IdentityHeaders.PERMISSIONS)));
                CurrentUserHolder.set(user);
                req.setAttribute(IdentityHeaders.REQUEST_ATTR_USER_ID, user.userId());
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(new JwtAuthenticationToken(user));
                SecurityContextHolder.setContext(ctx);
            } catch (NumberFormatException e) {
                log.warn("Invalid {} header: {}", IdentityHeaders.USER_ID, userIdHeader);
            }
        }
        try {
            chain.doFilter(req, res);
        } finally {
            CurrentUserHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private static String nullable(String s) { return s == null || s.isBlank() ? null : s; }

    private static long parseLong(String s) {
        if (s == null || s.isBlank()) return 0L;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0L; }
    }

    private static Set<String> parseSet(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        Set<String> out = new HashSet<>();
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    @Override
    public int getOrder() { return ORDER; }

    /**
     * Re-run on ASYNC dispatch. Spring Security's filter chain runs on
     * ASYNC dispatch by default; without this, an SSE/async controller
     * comes back through the chain with an empty SecurityContext (the
     * original dispatch's finally cleared it) and AuthorizationFilter
     * fires AccessDenied. The X-User-* headers are still on the
     * preserved request, so re-populating from them is the right thing.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() { return false; }
}
