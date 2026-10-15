package com.forward.hrms.audit.dto.response;

import com.forward.hrms.audit.entity.AuditEvent;

public final class TimelineEntryMapper {

    private TimelineEntryMapper() {}

    public static TimelineEntry of(AuditEvent e) {
        return new TimelineEntry(e.getId(), e.getAction(), e.getActorUserId(),
                e.getActorUsername(), e.getActorLabel(),
                e.getMessage(), e.getOccurredAt());
    }
}
