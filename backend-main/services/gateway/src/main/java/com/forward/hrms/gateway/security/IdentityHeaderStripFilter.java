package com.forward.hrms.gateway.security;

import com.forward.hrms.commons.web.security.IdentityHeaders;

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
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Strips any inbound {@code X-User-*} headers from external requests
 * <em>before</em> the security chain runs. A client that sends
 * {@code X-User-Id: 1} on the way in must NOT impersonate user 1 — the
 * gateway re-adds these headers itself after a successful JWT check
 * (see {@link IdentityHeaderInjectionFilter}).
 *
 * <p>This is the single place that enforces "the gateway is the only
 * source of identity headers"; everything else is defense in depth.
 */
public class IdentityHeaderStripFilter extends OncePerRequestFilter implements Ordered {

    /** Run earlier than the JWT filter — strip first, then authenticate. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 25;

    private static final Set<String> STRIPPED;

    static {
        Set<String> s = new java.util.HashSet<>();
        for (String h : IdentityHeaders.ALL) s.add(h.toLowerCase(Locale.ROOT));
        STRIPPED = Set.copyOf(s);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(new StrippingRequest(req), res);
    }

    @Override
    public int getOrder() { return ORDER; }

    private static final class StrippingRequest extends HttpServletRequestWrapper {
        StrippingRequest(HttpServletRequest req) { super(req); }

        private boolean stripped(String name) {
            return name != null && STRIPPED.contains(name.toLowerCase(Locale.ROOT));
        }

        @Override
        public String getHeader(String name) {
            return stripped(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return stripped(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> kept = new ArrayList<>();
            Enumeration<String> e = super.getHeaderNames();
            while (e.hasMoreElements()) {
                String n = e.nextElement();
                if (!stripped(n)) kept.add(n);
            }
            return Collections.enumeration(kept);
        }
    }
}
