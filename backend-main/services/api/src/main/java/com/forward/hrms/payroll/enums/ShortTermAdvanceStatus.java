package com.forward.hrms.payroll.enums;

/** Per-employee status within a short-term advance batch. */
public enum ShortTermAdvanceStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** The whole batch was cancelled before the advance was recovered. */
    CANCELLED
}
