package com.forward.hrms.employee.enums;

/**
 * Coarse role bucket on top of the seniority {@code level}. Drives a
 * handful of policy switches downstream (e.g. payroll's canteen rule
 * charges management weekly, supervisor / labour per-day).
 */
public enum GradeType {
    MANAGEMENT,
    SUPERVISOR,
    LABOUR
}
