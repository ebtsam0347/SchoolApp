package com.forward.hrms.commons.employeeevents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;

/**
 * Base class for the four services that consume {@link EmployeeEvents#TOPIC}.
 * Owns the envelope parse + the {@code Long}/{@code LocalDate} JsonNode
 * helpers that every listener used to redeclare. Subclasses keep their
 * service-specific {@code @KafkaListener @Transactional} method, call
 * {@link #dispatch(String)} from it, and override {@link #handle} to
 * switch on the event type.
 *
 * <p>Why a base class and not a registered handler-map: the {@code
 * @KafkaListener} groupId differs per service and the listener method
 * itself is the natural Spring-managed seam. A {@code dispatch} method
 * is the smallest thing that pays off.
 */
public abstract class EmployeeEventsConsumerSupport {

    private final ObjectMapper json;

    protected EmployeeEventsConsumerSupport(ObjectMapper json) {
        this.json = json;
    }

    /**
     * Parse the envelope ({@code eventType} + {@code payload}) and
     * dispatch via {@link #handle}. Returns silently on a malformed
     * envelope ({@code eventType == null}) — the outer listener's
     * {@code RetryAndDlqErrorHandler} will pick the row up if the read
     * itself throws.
     */
    protected final void dispatch(String message) throws IOException {
        JsonNode root = json.readTree(message);
        String eventType = root.path("eventType").asText(null);
        if (eventType == null) return;
        JsonNode payload = root.path("payload");
        // Replay the producer's actor onto this Kafka listener thread so
        // any audit/event the consumer emits in reaction is credited to
        // the original user, not "system". Listener threads have no
        // inbound HTTP request — without this, EventEnvelope.wrap reads
        // an empty CurrentUserHolder and the resulting audit row shows
        // the system as the actor.
        CurrentUser replayed = actorFromEnvelope(root);
        boolean userSet = false;
        if (replayed != null) {
            CurrentUserHolder.set(replayed);
            userSet = true;
        }
        try {
            handle(eventType, payload);
        } finally {
            if (userSet) CurrentUserHolder.clear();
        }
    }

    private static CurrentUser actorFromEnvelope(JsonNode root) {
        JsonNode idNode = root.path("actorUserId");
        long userId;
        if (idNode.isNumber()) {
            userId = idNode.asLong();
        } else if (idNode.isTextual() && !idNode.asText().isBlank()) {
            try { userId = Long.parseLong(idNode.asText()); }
            catch (NumberFormatException ex) { return null; }
        } else {
            return null;
        }
        if (userId <= 0) return null;
        JsonNode nameNode = root.path("actorUsername");
        String username = nameNode.isMissingNode() || nameNode.isNull() ? null : nameNode.asText(null);
        JsonNode labelNode = root.path("actorLabel");
        String fullName = labelNode.isMissingNode() || labelNode.isNull() ? null : labelNode.asText(null);
        // Roles/permissions aren't carried on the envelope — consumers that
        // need authorization must do their own checks. Leaving these empty
        // limits the replay to actor-credit on emitted events.
        return new CurrentUser(userId, username, fullName, 0L, Set.of(), Set.of());
    }

    /** Switch on the event type. Unknown types should be ignored (debug-log at most). */
    protected abstract void handle(String eventType, JsonNode payload);

    /** Numeric or numeric-string field on the payload, or null if missing/blank/unparseable. */
    public static Long readLong(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isNumber()) return v.asLong();
        if (v.isTextual() && !v.asText().isBlank()) {
            try { return Long.parseLong(v.asText()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    /** ISO-date field on the payload, or null if missing/null/unparseable. */
    public static LocalDate readDate(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        try {
            return LocalDate.parse(v.asText());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
