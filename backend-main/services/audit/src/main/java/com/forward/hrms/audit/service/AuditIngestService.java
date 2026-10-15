package com.forward.hrms.audit.service;

import com.forward.hrms.audit.enums.SkipReason;
import com.forward.hrms.audit.repository.AuditEventRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

/**
 * Parses an inbound audit envelope and persists it. Idempotent: a duplicate
 * {@code eventId} is a no-op (Kafka redelivery is the common case). Tolerant
 * of envelope evolution — fields not produced by today's emitters are
 * read from the JSON when present and left null otherwise.
 *
 * <p>Returns an {@link IngestOutcome}. The consumer turns
 * {@code SKIPPED} outcomes into {@code audit_dead_letters} rows so a
 * producer-side bug can't silently drop events past Kafka retention.
 *
 * Envelope shape (see commons-kafka {@code EventEnvelope} +
 * {@code AuditEmitter.AuditEvent}):
 * <pre>
 * {
 *   "eventId":        "uuid",
 *   "eventType":      "audit.&lt;entityType&gt;.&lt;action&gt;",
 *   "occurredAt":     "iso-8601",
 *   "correlationId":  "...",
 *   "actorUserId":    42,
 *   "actorUsername":  "FS-2026-0042",
 *   "actorLabel":     "Saqib Mushtaq",
 *   "payload": {
 *     "entityType":   "user",
 *     "entityId":     "1042",
 *     "action":       "deactivated",
 *     "before":       { ... },
 *     "after":        { ... },
 *     "changes":      { "field": { "from": ..., "to": ... } },
 *     "userVisible":  true,
 *     "message":      "Saqib was deactivated."
 *   },
 *   "metadata": { "ip": "203.0.113.5", "ua": "Mozilla/5.0" }
 * }
 * </pre>
 */
@Slf4j
@Service
public class AuditIngestService {

    private final AuditEventRepository repo;
    private final ObjectMapper json;

    /**
     * Uses the Spring-autowired Jackson 3 ObjectMapper (SB4 default).
     * Audit no longer ships its own mapper — the previous custom bean
     * was a hold-over from before the Jackson 3 cutover. Same mapper
     * now serializes responses and parses Kafka envelopes.
     */
    public AuditIngestService(AuditEventRepository repo, ObjectMapper json) {
        this.repo = repo;
        this.json = json;
    }

    @Transactional
    public IngestOutcome ingest(String sourceService, String rawJson) {
        JsonNode root;
        try {
            root = json.readTree(rawJson);
        } catch (Exception e) {
            // Jackson 3's readTree throws RuntimeException-ish JacksonException;
            // catch broadly so a malformed envelope doesn't kill the consumer.
            log.warn("Skipping malformed audit envelope from {}: {}", sourceService, e.getMessage());
            return IngestOutcome.skipped(SkipReason.MALFORMED_JSON, e.getMessage());
        }

        String eventId = text(root, "eventId");
        if (eventId == null || eventId.isBlank()) {
            // Synthesizing a fresh UUID would defeat the eventId-based
            // dedupe path: the same payload re-delivered tomorrow gets a
            // different UUID and inserts twice. Reject + log so the
            // emitter is forced to set eventId.
            log.warn("Rejecting audit envelope from {} missing eventId — emitter must set it", sourceService);
            return IngestOutcome.skipped(SkipReason.MISSING_EVENT_ID, "eventId missing or blank");
        }
        // §7.audit #7 — eventId column is varchar(64). Reject oversized
        // values at the boundary so the failure surfaces here as a clean
        // SKIPPED instead of an INSERT-time DataIntegrityViolation that
        // unwinds the transaction and the offset commit with it.
        if (eventId.length() > 64) {
            log.warn("Rejecting audit envelope from {} with oversized eventId ({} chars)",
                    sourceService, eventId.length());
            return IngestOutcome.skipped(SkipReason.OVERSIZED_EVENT_ID,
                    "eventId is " + eventId.length() + " chars (max 64)");
        }

        JsonNode payload = root.path("payload");
        String entityType = text(payload, "entityType");
        String entityId   = text(payload, "entityId");
        String action     = text(payload, "action");
        if (entityType == null || entityId == null || action == null) {
            log.warn("Skipping audit envelope from {} missing entityType/entityId/action: eventId={}",
                    sourceService, eventId);
            return IngestOutcome.skipped(SkipReason.MISSING_REQUIRED_PAYLOAD,
                    "payload.entityType/entityId/action: " + entityType + "/" + entityId + "/" + action);
        }

        LocalDateTime occurredAt = parseLocalDateTime(text(root, "occurredAt"));
        if (occurredAt == null) {
            log.warn("Rejecting audit envelope from {} with missing/unparseable occurredAt: eventId={}",
                    sourceService, eventId);
            return IngestOutcome.skipped(SkipReason.UNPARSEABLE_OCCURRED_AT,
                    "occurredAt=" + text(root, "occurredAt"));
        }

        // §7.audit #5 — store NULL when the producer didn't set a
        // correlationId. The previous fallback (eventId itself) made
        // "find every event in this correlation" return a single-row
        // misleading result. Real correlation comes from request-context
        // ids that producers must propagate; if they didn't, we'd rather
        // surface the gap as NULL than fake a one-element group.
        String correlationId = text(root, "correlationId");
        if (correlationId != null && correlationId.length() > 64) {
            log.warn("Truncating oversized correlationId from {} for eventId={}",
                    sourceService, eventId);
            correlationId = correlationId.substring(0, 64);
        }
        if (correlationId == null) {
            log.warn("Audit envelope from {} missing correlationId — storing NULL (eventId={})",
                    sourceService, eventId);
        }

        // §6.8 — single round-trip via INSERT … ON CONFLICT (event_id)
        // DO NOTHING. Postgres returns affected row count 1 for new rows,
        // 0 for duplicates. Replaces the existsByEventId pre-check + save()
        // pattern (two round-trips happy path, exception-unwind on race).
        int inserted = repo.insertIfAbsent(
                eventId,
                sourceService,
                entityType,
                entityId,
                action,
                longOrNull(root, "actorUserId"),
                text(root, "actorUsername"),
                text(root, "actorLabel"),
                jsonOrNull(payload.path("before")),
                jsonOrNull(payload.path("after")),
                jsonOrNull(payload.path("changes")),
                jsonOrNull(root.path("metadata")),
                correlationId,
                text(root.path("metadata"), "ip"),
                payload.path("userVisible").asBoolean(false),
                text(payload, "message"),
                occurredAt,
                LocalDateTime.now());
        return inserted > 0 ? IngestOutcome.inserted() : IngestOutcome.duplicate();
    }

    public record IngestOutcome(Status status, SkipReason reason, String detail) {
        public enum Status { INSERTED, DUPLICATE, SKIPPED }

        public static IngestOutcome inserted() { return new IngestOutcome(Status.INSERTED, null, null); }
        public static IngestOutcome duplicate() { return new IngestOutcome(Status.DUPLICATE, null, null); }
        public static IngestOutcome skipped(SkipReason reason, String detail) {
            return new IngestOutcome(Status.SKIPPED, reason, detail);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asString();
    }

    private static Long longOrNull(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isNumber()) return v.asLong();
        if (v.isString() && !v.asString().isBlank()) {
            try { return Long.parseLong(v.asString()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private String jsonOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        return n.toString();
    }

    private static LocalDateTime parseLocalDateTime(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try { return LocalDateTime.parse(iso); } catch (Exception ignored) { return null; }
    }
}
