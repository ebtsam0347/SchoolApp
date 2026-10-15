package com.forward.hrms.audit.service;

import com.forward.hrms.audit.dto.response.TimelineEntry;
import com.forward.hrms.audit.dto.response.TimelinePage;
import com.forward.hrms.audit.entity.AuditEvent;
import com.forward.hrms.audit.repository.AuditEventRepository;
import com.forward.hrms.audit.dto.response.TimelineEntryMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * User-facing timeline reads. Cursor pagination keyed on
 * {@code (occurredAt, id)}.
 */
@Service
@RequiredArgsConstructor
public class TimelineService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final AuditEventRepository repo;

    @Transactional(readOnly = true)
    public TimelinePage timeline(String entityType, String entityId,
                                 LocalDateTime from, LocalDateTime to,
                                 Integer limit, String cursor) {
        int size = clamp(limit);
        Cursor c = Cursor.parse(cursor);

        List<AuditEvent> rows = repo.findTimeline(entityType, entityId, from, to,
                c == null ? null : c.ts(),
                c == null ? null : c.id(),
                PageRequest.of(0, size + 1));

        boolean hasMore = rows.size() > size;
        List<AuditEvent> page = hasMore ? rows.subList(0, size) : rows;
        String next = hasMore
                ? new Cursor(page.get(page.size() - 1).getOccurredAt(),
                              page.get(page.size() - 1).getId()).encode()
                : null;

        return new TimelinePage(page.stream().map(TimelineEntryMapper::of).toList(), next);
    }

    private static int clamp(Integer limit) {
        if (limit == null || limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private record Cursor(LocalDateTime ts, Long id) {

        String encode() {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((ts.toString() + ":" + id).getBytes(StandardCharsets.UTF_8));
        }

        static Cursor parse(String raw) {
            if (raw == null || raw.isBlank()) return null;
            try {
                String s = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
                int sep = s.lastIndexOf(':');
                if (sep < 0) return null;
                return new Cursor(LocalDateTime.parse(s.substring(0, sep)), Long.parseLong(s.substring(sep + 1)));
            } catch (Exception e) {
                return null;
            }
        }
    }
}
