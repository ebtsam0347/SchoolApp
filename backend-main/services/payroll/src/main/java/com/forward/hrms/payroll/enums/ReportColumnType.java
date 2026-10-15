package com.forward.hrms.payroll.enums;

/** Cell type of an accounts-report column — drives formatting, Excel cell type and totals. */
public enum ReportColumnType {
    TEXT,
    INT,
    AMOUNT,
    /** Non-money number that isn't summed (days, hours). */
    DECIMAL,
    DATE,
    /** {@code YearMonth} value, printed "September 2026". */
    MONTH
}
