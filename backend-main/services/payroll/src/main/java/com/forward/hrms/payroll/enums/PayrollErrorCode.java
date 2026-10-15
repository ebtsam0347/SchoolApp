package com.forward.hrms.payroll.enums;

import com.forward.hrms.commons.core.error.ErrorCode;

public enum PayrollErrorCode implements ErrorCode {
    SALARY_NOT_FOUND,
    COMPONENT_NOT_FOUND,
    COMPONENT_INACTIVE,
    COMPONENT_CODE_TAKEN,
    COMPONENT_INVALID_CALC_TYPE,
    SALARY_LINE_NOT_FOUND,
    PAYROLL_RUN_NOT_FOUND,
    PAYROLL_RUN_DUPLICATE,
    PAYROLL_RUN_INVALID_STATE,
    /** The employee has no skip row on that run (never skipped, or already retried). */
    PAYROLL_RUN_SKIP_NOT_FOUND,
    /** Settlement creation blocked: a payslip already exists for this
     *  employee for the termination month — resolve it before settling. */
    PAYROLL_ALREADY_PROCESSED_FOR_PERIOD,
    PAYSLIP_NOT_FOUND,
    PAYSLIP_INVALID_STATE,
    OFF_CYCLE_NOT_FOUND,
    OFF_CYCLE_IMMUTABLE,
    /** The requested transition is not legal from the row's current status. */
    OFF_CYCLE_INVALID_STATE,
    /** Selected off-cycle payment type is absent from, or inactive in,
     *  employee-service's {@code off_cycle_payment_type} master. */
    OFF_CYCLE_TYPE_INVALID,
    /** employee-service could not be reached to validate the type. Distinct
     *  from OFF_CYCLE_TYPE_INVALID on purpose: "we could not check" must
     *  never be read as "the type is bad". */
    OFF_CYCLE_TYPE_LOOKUP_FAILED,
    /** Hard delete is retired — cancel an HR_APPROVED payment instead. */
    OFF_CYCLE_DELETE_RETIRED,
    /** Another writer changed the row between read and write. */
    OFF_CYCLE_CONCURRENT_UPDATE,
    BONUS_EVENT_NOT_FOUND,
    BONUS_EVENT_INVALID_STATE,
    BONUS_EVENT_PERCENTAGE_EXCEEDED,
    BONUS_INSTALLMENT_NOT_FOUND,
    BONUS_INSTALLMENT_INVALID_STATE,
    BONUS_NO_SETTINGS_NOT_FOUND,
    BONUS_NO_SETTINGS_DUPLICATE,
    BONUS_NO_SETTINGS_INVALID_STATE,
    BONUS_TRANSACTION_NOT_FOUND,
    INCREMENT_BATCH_NOT_FOUND,
    INCREMENT_BATCH_INVALID_STATE,
    INCREMENT_BATCH_HAS_ERRORS,
    INCREMENT_LINE_NOT_FOUND,
    INCREMENT_LINE_INVALID_STATE,
    SETTINGS_NOT_FOUND,
    BANK_DETAILS_NOT_FOUND,
    BANK_DETAILS_REQUIRED,
    BANK_DETAILS_NOT_REMOVABLE,
    BANK_NOT_FOUND,
    BANK_CODE_TAKEN,
    BANK_INACTIVE,
    FISCAL_YEAR_NOT_FOUND,
    FISCAL_YEAR_INVALID,
    PAYROLL_CONFIG_NOT_FOUND,
    EMPLOYEE_NOT_FOUND,
    NO_BASIC_SALARY,
    EMPLOYEE_INACTIVE,
    INVALID_PERIOD,

    // advances
    PERMANENT_ADVANCE_NOT_FOUND,
    PERMANENT_ADVANCE_INVALID_STATE,
    PERMANENT_ADVANCE_AMOUNT_INVALID,
    PERMANENT_ADVANCE_ALREADY_APPLIED,
    SHORT_TERM_BATCH_NOT_FOUND,
    SHORT_TERM_BATCH_INVALID_STATE,
    SHORT_TERM_BATCH_LINE_NOT_FOUND,
    SHORT_TERM_BATCH_DUPLICATE_EMPLOYEE,
    SHORT_TERM_ADVANCE_ALREADY_APPLIED,
    SHORT_TERM_BATCH_PERIOD_INVALID,

    // encashments
    LEAVE_ENCASHMENT_NOT_FOUND,
    LEAVE_ENCASHMENT_INVALID_STATE,
    LEAVE_ENCASHMENT_DUPLICATE_PERIOD,

    // final settlement
    /** No row in {@code employee_final_settlements} for the requested id. */
    FINAL_SETTLEMENT_NOT_FOUND,
    /** The requested transition is not legal from the row's current status. */
    FINAL_SETTLEMENT_INVALID_STATE,
    /** Employee already has a DRAFT / HR_APPROVED / PAID settlement. */
    FINAL_SETTLEMENT_ALREADY_ACTIVE,
    /** HR approval refused: the employee still holds uncleared company
     *  belongings. The blocking list rides on the ProblemDetail as
     *  {@code belongings} / {@code pendingCount} / {@code pendingAssetValue}. */
    BELONGING_CLEARANCE_PENDING,
    /** employee-service could not be reached to run the belonging clearance
     *  check. Distinct from BELONGING_CLEARANCE_PENDING on purpose: "we could
     *  not check" must never be read as "the employee holds nothing". */
    BELONGING_CLEARANCE_CHECK_FAILED,

    // upstream
    ATTENDANCE_SNAPSHOT_FAILED,
    /** lock-attendance found the attendance period not yet locked — the operator must close it first. */
    ATTENDANCE_PERIOD_NOT_LOCKED,
    /** Couldn't reach attendance-service to verify the period lock; not safe to read as "unlocked". */
    ATTENDANCE_LOCK_CHECK_FAILED,
    /** Couldn't reach the Fair Price Shop endpoint; not safe to read as "employee owes nothing". */
    FAIR_PRICE_SHOP_CHECK_FAILED,

    ATTENDANCE_TERMINATION_SUMMARY_FAILED,

    // §7.payroll round 2
    /** §7.payroll #63 — validation conflict surfaced through revise/update; was misnamed SALARY_NOT_FOUND. */
    SALARY_REVISION_INVALID,
    /** §7.payroll #67/#68 — component effectiveFrom must close (not invert) the existing line's range. */
    COMPONENT_DATE_INVALID,
    /** §7.payroll #69 — component create/update missing the required amount. */
    COMPONENT_AMOUNT_REQUIRED,
    /** Allowance set names a code that isn't an active allowance paid apart from salary. */
    ALLOWANCE_COMPONENT_INVALID,
    /** §7.payroll #36 — recordDeduction cannot exceed the advance's remaining balance. */
    PERMANENT_ADVANCE_DEDUCTION_EXCEEDS_REMAINING,
    /** Deduction id doesn't exist on that advance. */
    PERMANENT_ADVANCE_DEDUCTION_NOT_FOUND,
    /** Only MANUAL deductions are deleted directly; other sources are undone at their source. */
    PERMANENT_ADVANCE_DEDUCTION_NOT_MANUAL,
    /** §7.payroll #45 — encashment requires daysEncashed > 0. */
    LEAVE_ENCASHMENT_DAYS_INVALID,
    /** Caller targeted a closed-out salary line (effective_to is set). */
    SALARY_LINE_NOT_CURRENT,

    // reports
    /** No row in {@code report_jobs} for the requested id. */
    REPORT_JOB_NOT_FOUND,
    /** Caller asked to download a job that's still QUEUED / RUNNING / FAILED. */
    REPORT_JOB_NOT_READY,
    /** A report query parameter outside its allowed values (e.g. payslip {@code layout}). */
    REPORT_PARAM_INVALID,
    /** Unknown accounts report id. */
    ACCOUNTS_REPORT_NOT_FOUND,
    /** Accounts report filter missing a required value or inconsistent (card / date range, format). */
    ACCOUNTS_REPORT_FILTER_INVALID,
    /** employee-service lookup failed while resolving report rows — refuse rather than print placeholders. */
    ACCOUNTS_REPORT_EMPLOYEE_LOOKUP_FAILED,
    /** Bank letter asked for a bank whose company account number / IBAN isn't configured on {@code banks}. */
    ACCOUNTS_REPORT_BANK_ACCOUNT_MISSING,
    /** Slip / letter / ledger requested for filters that match nothing. */
    ACCOUNTS_REPORT_NOTHING_TO_PRINT,
    /** Document endpoint called for a report that isn't a slip, letter or ledger. */
    ACCOUNTS_REPORT_NOT_A_DOCUMENT,

    /** /approvals/history — missing dates, inverted range, or window over the cap. */
    APPROVAL_HISTORY_DATE_RANGE_INVALID,

    ANNUAL_LEAVE_BALANCE_NOT_FOUND
}
