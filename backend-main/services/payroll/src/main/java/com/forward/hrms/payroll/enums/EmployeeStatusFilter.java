package com.forward.hrms.payroll.enums;

/**
 * Legacy "Emp Status" radio. Active is everyone not {@code TERMINATED}
 * (notice period, leave and suspension still draw salary / owe advances);
 * Non-Active is {@code TERMINATED}. {@code ALL} still has to be honoured —
 * it means both, not "ignore the filter and default to active".
 */
public enum EmployeeStatusFilter {
    ACTIVE,
    NON_ACTIVE,
    ALL
}
