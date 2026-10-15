package com.forward.hrms.commons.core.context;

import java.util.Optional;

/**
 * Per-request data populated by the web layer's correlation filter. Holds
 * what the audit listener needs (IP) plus user agent for log enrichment.
 */
public record RequestContext(String correlationId, String ipAddress, String userAgent) {

    private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

    public static Optional<RequestContext> current() { return Optional.ofNullable(CURRENT.get()); }
    public static void set(RequestContext ctx) { CURRENT.set(ctx); }
    public static void clear() { CURRENT.remove(); }
}
