package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.ReportJobStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;

/**
 * Async-report queue row. Created QUEUED by the request handler, picked
 * up by {@link com.forward.hrms.payroll.service.ReportJobRunner}, flipped
 * to READY (with {@code outputKey}) or FAILED.
 *
 * <p>{@code params} is a JSON string the type-specific handler parses.
 * Stored as {@code jsonb} (same pattern as saga_state.payload) so the
 * column stays queryable from psql.
 */
@Entity
@Table(name = "report_jobs")
@Getter
@Setter
public class ReportJob extends AuditedEntity {

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "params", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String params = "{}";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReportJobStatus status = ReportJobStatus.QUEUED;

    @Column(name = "requested_by", nullable = false, length = 64)
    private String requestedBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "output_key", length = 512)
    private String outputKey;

    @Column(name = "output_size")
    private Long outputSize;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;
}
