package com.forward.hrms.commons.approval.entity;

import com.forward.hrms.commons.approval.enums.ResolverKind;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One stage in a request's approval chain. Snapshotted at submit; the
 * {@code assignedTo} stays put even when an active delegation lets a
 * delegate act on the assignee's behalf — the resolver expands "who can
 * act" dynamically against the live {@code ApprovalDelegation} table.
 */
@Entity
@Table(name = "approval_steps")
@Getter
@Setter
public class ApprovalStep extends AuditedEntity {

    @Column(name = "request_type", nullable = false, length = 24)
    private String requestType;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "stage_no", nullable = false)
    private int stageNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolver_kind", nullable = false, length = 16)
    private ResolverKind resolverKind;

    /** Snapshot of the stage's gating permission for {@code PERMISSION} kind; null for {@code MANAGER}. */
    @Column(name = "approve_permission", length = 64)
    private String approvePermission;

    /** Snapshot of the stage's target for {@code DEPARTMENT_HEAD}/{@code GRADE} kind; null otherwise. */
    @Column(name = "resolver_target", length = 64)
    private String resolverTarget;

    /** Snapshot of the stage's display label ("MANAGER", "HR", "Accounts"). Drives event payloads + DTOs. */
    @Column(name = "label", nullable = false, length = 48)
    private String label;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private StepStatus status;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "note")
    private String note;
}
