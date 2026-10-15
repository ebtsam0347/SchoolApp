package com.forward.hrms.commons.approval.entity;

import com.forward.hrms.commons.approval.enums.ResolverKind;
import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One stage in the HR-maintained approval chain for a request type. The
 * ordered set of active rows for a {@code requestType} is the chain that
 * {@code ApprovalService.openChain} snapshots into {@code approval_steps}
 * at submit. Editing this catalog never disturbs in-flight requests —
 * they run on the snapshot they were opened with.
 */
@Entity
@Table(name = "approval_workflow_stage")
@Getter
@Setter
public class ApprovalWorkflowStage extends AuditedEntity {

    @Column(name = "request_type", nullable = false, length = 24)
    private String requestType;

    @Column(name = "stage_no", nullable = false)
    private int stageNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolver_kind", nullable = false, length = 16)
    private ResolverKind resolverKind;

    /** The permission a {@code PERMISSION}-kind stage gates on; null for {@code MANAGER}. */
    @Column(name = "approve_permission", length = 64)
    private String approvePermission;

    /** The target (department code / grade code) a {@code DEPARTMENT_HEAD}/{@code GRADE}-kind stage gates on. */
    @Column(name = "resolver_target", length = 64)
    private String resolverTarget;

    /** Display name carried onto the step + every stage event ("MANAGER", "HR", "Accounts"). */
    @Column(name = "label", nullable = false, length = 48)
    private String label;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
