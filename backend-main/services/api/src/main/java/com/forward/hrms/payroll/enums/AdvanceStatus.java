package com.forward.hrms.payroll.enums;

/**
 * Shared lifecycle for permanent advances and short-term advance batches.
 * Three-stage approval (Manager, then HR, then Accounts). APPROVED is the
 * live state the payroll run deducts against — there is no disbursement or
 * closing step.
 */
public enum AdvanceStatus {
    PENDING,
    MANAGER_APPROVED,
    HR_APPROVED,
    APPROVED,
    REJECTED,
    CANCELLED
}
