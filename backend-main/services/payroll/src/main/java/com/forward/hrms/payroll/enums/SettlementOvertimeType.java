package com.forward.hrms.payroll.enums;

/**
 * Overtime classification stored on the Final Settlement's overtime
 * snapshot. Only {@code NORMAL} is supported today — the settlement
 * intentionally represents Normal Overtime only, never Extra/Special.
 * Kept as an enum (rather than a bare literal) so the column is
 * self-documenting and forward-compatible if a future requirement adds
 * another settlement-eligible overtime category.
 */
public enum SettlementOvertimeType {
    NORMAL
}
