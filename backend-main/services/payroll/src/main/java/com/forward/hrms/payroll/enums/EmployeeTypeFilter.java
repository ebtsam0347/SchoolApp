package com.forward.hrms.payroll.enums;

/**
 * Legacy "Employee Type" radio (F7). Production = the employee's department
 * is flagged {@code is_production} in employee-service; everyone else is
 * Non-Production.
 */
public enum EmployeeTypeFilter {
    ALL,
    PRODUCTION,
    NON_PRODUCTION
}
