package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.PayrollRunStatus;
import com.forward.hrms.payroll.repository.PayrollRunRepository;

/**
 * Refuses writes that target a missing or terminal payroll run.
 * Used by LeaveEncashmentService.markPaid.
 * POSTED / FAILED / CANCELLED runs are immutable; a stale or misrouted call
 * shouldn't quietly write a deduction marker pointing at a frozen run.
 */
public final class PayrollRunGuard {

    private PayrollRunGuard() {}

    public static void requireWritable(PayrollRunRepository runs, long payrollRunId) {
        var run = runs.findById(payrollRunId).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.PAYROLL_RUN_NOT_FOUND,
                        "payroll run " + payrollRunId + " not found"));
        var s = run.getStatus();
        if (s == PayrollRunStatus.POSTED || s == PayrollRunStatus.FAILED || s == PayrollRunStatus.CANCELLED) {
            throw new ConflictError(PayrollErrorCode.PAYROLL_RUN_INVALID_STATE,
                    "payroll run " + payrollRunId + " is " + s + " — no further writes allowed");
        }
    }
}
