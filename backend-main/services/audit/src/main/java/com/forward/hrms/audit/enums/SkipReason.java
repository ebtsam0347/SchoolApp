package com.forward.hrms.audit.enums;

/**
 * Why an inbound audit envelope was rejected at parse / validation time
 * by {@code AuditIngestService}. Enum values are persisted verbatim into
 * {@code audit_dead_letters.reason} (varchar(64)), so renames are
 * breaking — only ever add new values, never rename or reorder.
 */
public enum SkipReason {
    /** Body was not parseable JSON. */
    MALFORMED_JSON,
    /** Envelope had no {@code eventId}, or it was blank. */
    MISSING_EVENT_ID,
    /** {@code eventId} exceeded the 64-char column width. */
    OVERSIZED_EVENT_ID,
    /** {@code payload.entityType} / {@code payload.entityId} / {@code payload.action} was missing. */
    MISSING_REQUIRED_PAYLOAD,
    /** {@code occurredAt} missing or not a {@code LocalDateTime}. */
    UNPARSEABLE_OCCURRED_AT
}
