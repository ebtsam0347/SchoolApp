package com.forward.hrms.payroll.enums;

/**
 * Which review stage a PENDING/HR_APPROVED advance is currently sitting at —
 * lets the pending-approvals inbox merge both stages into one list while
 * still telling the caller (and the cross-service composite) which action
 * is actionable on that row.
 */
public enum AdvanceApprovalStage {
    MANAGER_APPROVAL,
    HR_APPROVAL,
    ACCOUNTS_APPROVAL
}
