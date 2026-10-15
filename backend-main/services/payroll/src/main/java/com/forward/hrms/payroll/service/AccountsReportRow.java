package com.forward.hrms.payroll.service;

import java.util.Map;

/**
 * One row as a data source produces it: the employee it belongs to plus
 * the report's own (non-employee) column values. The report service adds
 * the employee columns, filters, sorts and totals.
 */
public record AccountsReportRow(long employeeId, Map<String, Object> values) {}
