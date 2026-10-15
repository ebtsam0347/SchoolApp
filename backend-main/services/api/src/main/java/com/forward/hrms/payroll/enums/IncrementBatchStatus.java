package com.forward.hrms.payroll.enums;

public enum IncrementBatchStatus {
    DRAFT,
    APPLIED,
    /** §7.payroll #32 — apply ran with per-line REQUIRES_NEW; some lines failed. */
    PARTIALLY_APPLIED,
    CANCELLED
}
