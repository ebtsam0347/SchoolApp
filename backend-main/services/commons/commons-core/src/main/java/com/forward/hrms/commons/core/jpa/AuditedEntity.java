package com.forward.hrms.commons.core.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;

/**
 * Base class for every business entity. The five audit columns are
 * populated by {@link AuditedEntityListener}; never set them by hand.
 *
 * <p>Bridge tables (pure {@code (a_id, b_id)} joins) and system tables
 * (outbox, saga_state, audit_events) DO NOT extend this — extend
 * {@link BaseLongIdEntity} directly instead.
 */
@MappedSuperclass
@EntityListeners(AuditedEntityListener.class)
@Getter
@Setter
public abstract class AuditedEntity extends BaseLongIdEntity {

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
}
