package com.forward.hrms.commons.kafka.outbox;

import com.forward.hrms.commons.kafka.event.KafkaTopics;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convenience over {@link Outbox#publish}: produces a normalized audit
 * event onto the per-service audit topic. {@code userVisible=true} means
 * the event should appear on the user-facing timeline.
 */
@RequiredArgsConstructor
public class AuditEmitter {

    private final Outbox outbox;
    private final String serviceName;

    public void emit(String entityType, String entityId, String action,
                     Object before, Object after, boolean userVisible) {
        emit(entityType, entityId, action, before, after, userVisible, null);
    }

    /**
     * Same as {@link emit(String, String, Object, Object, Object, boolean)}
     * but with a producer-rendered one-line {@code message} for the
     * user-visible timeline.` Consumers don't template anything (per
     * {@code TimelineEntry} doctrine); whatever the producer passes is what
     * the FE shows in the row body. Keep it short — "Designation changed",
     * "Role HR assigned" — the changes JSON carries the diff detail.
     */
    public void emit(String entityType, String entityId, String action,
                     Object before, Object after, boolean userVisible, String message) {
        AuditEvent evt = new AuditEvent(entityType, entityId, action, before, after, userVisible, message);
        outbox.publish(KafkaTopics.audit(serviceName),
                entityType + ":" + entityId,
                "audit." + entityType + "." + action,
                evt,
                true);
    }

    /**
     * Emit an audit row that survives the caller's transaction rollback.
     * Use this for the "we refused this and threw" path — login.failed,
     * approval-step denied, etc. — where {@link #emit} on its own would
     * insert into the outbox in the caller's tx and lose the row when the
     * subsequent throw rolls back. Runs the underlying outbox insert in
     * {@link Propagation#REQUIRES_NEW}, so the audit row commits on its
     * own boundary.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void emitOnFailure(String entityType, String entityId, String action,
                              Object before, Object after, boolean userVisible) {
        emit(entityType, entityId, action, before, after, userVisible, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void emitOnFailure(String entityType, String entityId, String action,
                              Object before, Object after, boolean userVisible, String message) {
        emit(entityType, entityId, action, before, after, userVisible, message);
    }

    public record AuditEvent(
            String entityType,
            String entityId,
            String action,
            Object before,
            Object after,
            boolean userVisible,
            String message
    ) {}
}
