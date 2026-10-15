package com.forward.hrms.commons.approval.entity;

import com.forward.hrms.commons.approval.enums.DelegationStatus;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Approval delegation. {@code requestTypes} is a Postgres
 * {@code varchar(24)[]} — null means all request types.
 * Stored as plain strings so adding new request type keys
 * doesn't break already-persisted rows.
 */
@Entity
@Table(name = "approval_delegations")
@Getter
@Setter
public class ApprovalDelegation extends AuditedEntity {

    @Column(name = "delegator_id", nullable = false)
    private Long delegatorId;

    @Column(name = "delegate_id", nullable = false)
    private Long delegateId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "request_types", columnDefinition = "varchar(24)[]")
    private List<String> requestTypes;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DelegationStatus status;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;
}
