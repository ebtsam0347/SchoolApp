package com.forward.hrms.commons.saga;

import com.forward.hrms.commons.core.jpa.BaseLongIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;

/**
 * Lives in the service's own schema. Inserted inside the kickoff
 * transaction by {@link SagaService#start}, then driven step-by-step
 * by {@link SagaRunner}.
 *
 * <p>System table — does NOT extend {@code AuditedEntity}. The
 * {@code created_by/updated_by/ip_address} columns are populated by
 * the substrate (start: from {@code CurrentUserHolder} +
 * {@code RequestContext}; advance: {@code system}, no IP).
 */
@Entity
@Table(name = "saga_state",
        uniqueConstraints = @UniqueConstraint(
                name = "saga_state_uk",
                columnNames = {"saga_name", "correlation_key"}),
        indexes = @Index(
                name = "idx_saga_state_due",
                columnList = "status, next_attempt_at"))
@Getter
@Setter
public class SagaState extends BaseLongIdEntity {

    @Column(name = "saga_name", nullable = false, length = 64)
    private String sagaName;

    @Column(name = "correlation_key", nullable = false, length = 128)
    private String correlationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private Status status = Status.RUNNING;

    @Column(name = "current_step", nullable = false, length = 64)
    private String currentStep;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String payload;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 64)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    @Column(name = "ip_address", columnDefinition = "inet")
    @ColumnTransformer(write = "?::inet")
    private String ipAddress;

    public enum Status { RUNNING, COMPLETED, FAILED }
}
