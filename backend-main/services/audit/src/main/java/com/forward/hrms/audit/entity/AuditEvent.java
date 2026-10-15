package com.forward.hrms.audit.entity;

import com.forward.hrms.commons.core.jpa.BaseLongIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Append-only audit row. Doesn't extend the standard audited entity — this
 * IS the audit. Lifecycle fields ({@code occurredAt}, {@code ingestedAt},
 * {@code actorUsername}) play that role. {@code eventId} mirrors the
 * envelope id from the emitting service and is the idempotency key.
 */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
public class AuditEvent extends BaseLongIdEntity {

    /** §6.6 — uniqueness is a table-level {@code (event_id, occurred_at)}
     *  composite (Postgres requires the partition key in every unique
     *  index). The single-column unique flag is dropped here so the
     *  annotation doesn't lie about the constraint. */
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "source_service", nullable = false, length = 32)
    private String sourceService;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", length = 64)
    private String actorUsername;

    @Column(name = "actor_label", length = 128)
    private String actorLabel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json", columnDefinition = "jsonb")
    private String beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json", columnDefinition = "jsonb")
    private String afterJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", columnDefinition = "jsonb")
    private String changes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    /**
     * §7.audit #5 — relaxed to nullable in V0004. Producers should always
     * populate it; missing is now stored as NULL (logged at warn) instead
     * of being papered over with the eventId.
     */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "ip_address", columnDefinition = "inet")
    @ColumnTransformer(write = "?::inet")
    private String ipAddress;

    @Column(name = "user_visible", nullable = false)
    private boolean userVisible;

    @Column(name = "message")
    private String message;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "ingested_at", nullable = false, updatable = false)
    private LocalDateTime ingestedAt;
}
