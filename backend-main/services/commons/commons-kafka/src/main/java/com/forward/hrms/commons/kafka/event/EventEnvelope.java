package com.forward.hrms.commons.kafka.event;

import com.forward.hrms.commons.core.context.CorrelationId;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.context.RequestContext;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Standard wrapper for every event we publish — give consumers a uniform
 * shape so they don't each invent their own. {@code payload} is the
 * service-specific event record. {@code metadata} carries request-scoped
 * context (currently {@code ip} and {@code ua}) when the producing path
 * runs inside an HTTP request — null/empty for events emitted from
 * Kafka consumers or scheduled jobs.
 *
 * <p>{@code actorLabel} is the display name from the JWT {@code name}
 * claim, threaded through {@code X-User-FullName} on inter-service
 * calls. Audit-service stores it on the event row so the BFF can
 * render the timeline without an extra auth/users/by-ids round-trip.
 */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        LocalDateTime occurredAt,
        String correlationId,
        Long actorUserId,
        String actorUsername,
        String actorLabel,
        Map<String, String> metadata,
        T payload
) {
    public static <T> EventEnvelope<T> wrap(String eventType, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                LocalDateTime.now(),
                CorrelationId.get().orElse(null),
                CurrentUserHolder.get().map(u -> u.userId()).orElse(null),
                CurrentUserHolder.get().map(u -> u.username()).orElse(null),
                CurrentUserHolder.get().map(u -> u.fullName()).orElse(null),
                requestMetadata(),
                payload);
    }

    private static Map<String, String> requestMetadata() {
        return RequestContext.current().map(ctx -> {
            Map<String, String> m = new LinkedHashMap<>();
            if (ctx.ipAddress() != null && !ctx.ipAddress().isBlank()) m.put("ip", ctx.ipAddress());
            if (ctx.userAgent() != null && !ctx.userAgent().isBlank()) m.put("ua", ctx.userAgent());
            return m.isEmpty() ? null : m;
        }).orElse(null);
    }
}
