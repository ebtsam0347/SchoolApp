package com.forward.hrms.audit.dto.response;

import com.forward.hrms.audit.entity.AuditEvent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class AuditEventDtoMapper {

    private AuditEventDtoMapper() {}

    public static AuditEventDto of(AuditEvent e, ObjectMapper json) {
        return new AuditEventDto(
                e.getId(),
                e.getEventId(),
                e.getSourceService(),
                e.getEntityType(),
                e.getEntityId(),
                e.getAction(),
                e.getActorUserId(),
                e.getActorUsername(),
                e.getActorLabel(),
                tree(json, e.getBeforeJson()),
                tree(json, e.getAfterJson()),
                tree(json, e.getChanges()),
                tree(json, e.getMetadata()),
                e.getCorrelationId(),
                e.getIpAddress(),
                e.isUserVisible(),
                e.getMessage(),
                e.getOccurredAt(),
                e.getIngestedAt()
        );
    }

    private static JsonNode tree(ObjectMapper json, String s) {
        if (s == null || s.isBlank()) return null;
        try { return json.readTree(s); } catch (Exception ignored) { return null; }
    }
}
