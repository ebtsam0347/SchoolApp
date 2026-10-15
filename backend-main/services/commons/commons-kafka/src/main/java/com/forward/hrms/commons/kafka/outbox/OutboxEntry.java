package com.forward.hrms.commons.kafka.outbox;

import com.forward.hrms.commons.core.jpa.BaseLongIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;

/**
 * Lives in the service's own schema. Written inside the business
 * transaction by {@link Outbox#publish}, then drained by
 * {@link OutboxPublisher}.
 *
 * <p>System table — does NOT extend {@code AuditedEntity}; the per-row
 * timestamps below are the audit.
 */
@Entity
@Table(name = "outbox", indexes = {
        @Index(name = "idx_outbox_pending", columnList = "status, next_attempt_at"),
        @Index(name = "idx_outbox_topic", columnList = "topic, status")
})
@Getter
@Setter
public class OutboxEntry extends BaseLongIdEntity {

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Column(name = "message_key", length = 256)
    private String messageKey;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String payload;

    @Column(name = "is_audit", nullable = false)
    private boolean audit;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public enum Status { PENDING, SENT, FAILED }
}
