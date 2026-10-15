package com.forward.hrms.audit.service;

import com.forward.hrms.audit.entity.AuditEvent;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Opaque cursor for {@code (occurredAt desc, id desc)} pagination.
 * Mirrors the encoding used by {@link TimelineService} so the FE can
 * treat all cursor-paginated audit endpoints the same way.
 */
public record EntityHistoryCursor(LocalDateTime ts, Long id) {

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((ts.toString() + ":" + id).getBytes(StandardCharsets.UTF_8));
    }

    public static EntityHistoryCursor parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String s = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            int sep = s.lastIndexOf(':');
            if (sep < 0) return null;
            return new EntityHistoryCursor(LocalDateTime.parse(s.substring(0, sep)), Long.parseLong(s.substring(sep + 1)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * From a result list of size up to {@code requested + 1}, return
     * the next cursor to send back (or null if exhausted) and trim
     * the result to the requested size in-place.
     */
    public static String nextCursorFor(List<AuditEvent> rows, int requested) {
        if (rows.size() <= requested) return null;
        AuditEvent last = rows.get(requested - 1);
        return new EntityHistoryCursor(last.getOccurredAt(), last.getId()).encode();
    }
}
