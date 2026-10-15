package com.forward.hrms.employee.enums;

/**
 * Pakistan manufacturing convention. DIRECT = production-line / labour
 * paid for time on the floor. INDIRECT = office / admin / management.
 * Drives headcount reporting and OT-eligibility defaults.
 */
public enum DesignationCategory {
    DIRECT,
    INDIRECT
}
