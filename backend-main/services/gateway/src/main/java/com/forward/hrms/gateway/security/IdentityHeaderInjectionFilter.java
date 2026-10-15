package com.forward.hrms.gateway.security;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.web.security.IdentityHeaders;
import com.forward.hrms.commons.web.security.JwtAuthenticationFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * After {@link JwtAuthenticationFilter} populates {@link CurrentUserHolder},
 * this filter wraps the request with {@code X-User-*} headers (see
 * {@link IdentityHeaders}). Spring Cloud Gateway server-webmvc reads
 * headers from the wrapped request when it builds the outbound proxy
 * request, so downstream services see the trusted identity.
 *
 * <p>If the request is unauthenticated (e.g. {@code /auth/login}), no
 * headers are injected — downstream sees no identity.
 */
public class IdentityHeaderInjectionFilter extends OncePerRequestFilter implements Ordered {

    /** One slot after the JWT filter, before the security chain dispatches. */
    public static final int ORDER = JwtAuthenticationFilter.ORDER + 1;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Optional<CurrentUser> user = CurrentUserHolder.get();
        if (user.isEmpty()) {
            chain.doFilter(req, res);
            return;
        }
        chain.doFilter(new InjectingRequest(req, user.get()), res);
    }

    @Override
    public int getOrder() { return ORDER; }

    private static final class InjectingRequest extends HttpServletRequestWrapper {

        private final Map<String, String> injected;

        InjectingRequest(HttpServletRequest req, CurrentUser u) {
            super(req);
            Map<String, String> m = new HashMap<>();
            m.put(IdentityHeaders.USER_ID.toLowerCase(Locale.ROOT), String.valueOf(u.userId()));
            if (u.username() != null) m.put(IdentityHeaders.USERNAME.toLowerCase(Locale.ROOT), u.username());
            if (u.fullName() != null && !u.fullName().isBlank()) {
                m.put(IdentityHeaders.FULL_NAME.toLowerCase(Locale.ROOT), u.fullName());
            }
            if (u.sessionId() > 0) m.put(IdentityHeaders.SESSION_ID.toLowerCase(Locale.ROOT), String.valueOf(u.sessionId()));
            if (!u.roles().isEmpty()) m.put(IdentityHeaders.ROLES.toLowerCase(Locale.ROOT), String.join(",", u.roles()));
            if (!u.permissions().isEmpty()) m.put(IdentityHeaders.PERMISSIONS.toLowerCase(Locale.ROOT), String.join(",", u.permissions()));
            this.injected = Map.copyOf(m);
        }

        private String injected(String name) {
            return name == null ? null : injected.get(name.toLowerCase(Locale.ROOT));
        }

        @Override
        public String getHeader(String name) {
            String v = injected(name);
            return v != null ? v : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String v = injected(name);
            return v != null
                    ? Collections.enumeration(List.of(v))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> all = new ArrayList<>();
            Enumeration<String> e = super.getHeaderNames();
            while (e.hasMoreElements()) {
                String n = e.nextElement();
                // Only add the original name if we're not overriding it.
                if (injected(n) == null) all.add(n);
            }
            for (String h : IdentityHeaders.ALL) {
                if (injected.containsKey(h.toLowerCase(Locale.ROOT))) all.add(h);
            }
            return Collections.enumeration(all);
        }
    }
}
