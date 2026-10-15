package com.forward.hrms.gateway.security;

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
import java.util.Set;

/**
 * Forwards the real client IP / scheme / host to downstream services.
 *
 * <p>Without this filter, downstream services see the gateway container's
 * IP (e.g. {@code 172.23.0.9}) instead of the caller's, so audit columns
 * record the wrong source.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Strip any inbound {@code X-Forwarded-*} / {@code X-Real-IP}
 *       headers — clients can't be trusted to set them.</li>
 *   <li>Set fresh values from the actual TCP peer
 *       ({@code req.getRemoteAddr()}, {@code getScheme()},
 *       {@code getServerName()}).</li>
 * </ol>
 *
 * <p>If the gateway itself ever runs behind another L7 proxy (e.g. an
 * AWS ALB or a k3s ingress controller terminating TLS), this filter
 * needs an allow-list of trusted upstream IPs and should accept the
 * inbound {@code X-Forwarded-For} when it came from one of them. Not
 * needed in the current dev / UAT topology where the gateway is the
 * single edge.
 */
public class ClientInfoForwardingFilter extends OncePerRequestFilter implements Ordered {

    /** Run before the strip filter so injected values flow through. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 24;

    private static final Set<String> STRIPPED;

    static {
        Set<String> s = new java.util.HashSet<>();
        for (String h : new String[] {
                "X-Forwarded-For", "X-Forwarded-Proto", "X-Forwarded-Host",
                "X-Forwarded-Port", "X-Real-IP", "Forwarded"
        }) s.add(h.toLowerCase(Locale.ROOT));
        STRIPPED = Set.copyOf(s);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(new ForwardingRequest(req), res);
    }

    @Override
    public int getOrder() { return ORDER; }

    private static final class ForwardingRequest extends HttpServletRequestWrapper {

        private final Map<String, String> injected;

        ForwardingRequest(HttpServletRequest req) {
            super(req);
            Map<String, String> m = new HashMap<>();
            m.put("x-forwarded-for", req.getRemoteAddr());
            m.put("x-real-ip", req.getRemoteAddr());
            m.put("x-forwarded-proto", req.getScheme());
            m.put("x-forwarded-host", req.getServerName());
            m.put("x-forwarded-port", String.valueOf(req.getServerPort()));
            this.injected = Map.copyOf(m);
        }

        private boolean stripped(String name) {
            return name != null && STRIPPED.contains(name.toLowerCase(Locale.ROOT));
        }

        private String injected(String name) {
            return name == null ? null : injected.get(name.toLowerCase(Locale.ROOT));
        }

        @Override
        public String getHeader(String name) {
            String v = injected(name);
            if (v != null) return v;
            return stripped(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String v = injected(name);
            if (v != null) return Collections.enumeration(List.of(v));
            return stripped(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> kept = new ArrayList<>();
            Enumeration<String> e = super.getHeaderNames();
            while (e.hasMoreElements()) {
                String n = e.nextElement();
                if (!stripped(n) && injected(n) == null) kept.add(n);
            }
            // Add the canonical names we own.
            kept.add("X-Forwarded-For");
            kept.add("X-Real-IP");
            kept.add("X-Forwarded-Proto");
            kept.add("X-Forwarded-Host");
            kept.add("X-Forwarded-Port");
            return Collections.enumeration(kept);
        }
    }
}
