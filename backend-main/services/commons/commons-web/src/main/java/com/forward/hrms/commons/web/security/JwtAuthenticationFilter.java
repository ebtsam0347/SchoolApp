package com.forward.hrms.commons.web.security;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.Ordered;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates RS256 access tokens from {@code Authorization: Bearer <jwt>}.
 * On success: populates Spring's SecurityContext + the thread-local
 * {@link CurrentUserHolder}. On failure: silently leaves the request
 * unauthenticated — Spring Security's authorization rules then decide
 * whether to 401/403.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter implements Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 30;

    private final JwtParser parser;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims c = parser.parseSignedClaims(header.substring(7)).getPayload();
                CurrentUser user = new CurrentUser(
                        Long.parseLong(c.getSubject()),
                        c.get("username", String.class),
                        c.get("name", String.class),
                        readLong(c, "sid"),
                        readSet(c, "roles"),
                        readSet(c, "permissions"));
                CurrentUserHolder.set(user);
                req.setAttribute(IdentityHeaders.REQUEST_ATTR_USER_ID, user.userId());
                // Spring Security 6 best practice: create a fresh context
                // and call setContext, rather than mutating whatever
                // getContext() returns (which can be a shared empty
                // template across threads).
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(new JwtAuthenticationToken(user));
                SecurityContextHolder.setContext(ctx);
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("Rejected JWT: {}", e.toString());
            }
        }
        try {
            chain.doFilter(req, res);
        } finally {
            CurrentUserHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private static long readLong(Claims c, String key) {
        Object v = c.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) { return 0L; }
        }
        return 0L;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> readSet(Claims c, String key) {
        Object v = c.get(key);
        if (v instanceof List<?> l) return new HashSet<>((List<String>) l);
        if (v instanceof String s && !s.isBlank()) return Set.of(s.split("[,\\s]+"));
        return Set.of();
    }

    @Override
    public int getOrder() { return ORDER; }
}
