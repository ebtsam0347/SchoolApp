package com.forward.hrms.payroll.enums;

/**
 * Legacy "Payment Mode" radio (F6). Matched against the employee's current
 * {@code employee_payroll_settings.payment_mode} — payslips don't store the
 * mode they were paid by.
 */
public enum PaymentModeFilter {
    ALL,
    CASH,
    BANK_TRANSFER
}
