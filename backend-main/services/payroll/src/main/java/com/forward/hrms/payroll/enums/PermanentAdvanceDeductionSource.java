package com.forward.hrms.payroll.enums;

/**
 * Where a permanent-advance deduction was recovered from. Paired with
 * {@code source_ref_id} on the deduction row so undoing the source deletes
 * exactly its rows.
 */
public enum PermanentAdvanceDeductionSource {
    /** Payroll-run installment; ref = payroll run id. */
    SALARY,
    /** Taken from a bonus; ref = bonus transaction id. */
    BONUS,
    /** Taken from annual-leave encashment; ref = annual leave transaction id. */
    ANNUAL_LEAVE,
    /** Recorded by hand by HR / Accounts; no ref. The only directly deletable source. */
    MANUAL,
    /** Closing entry of a final settlement; ref = final settlement id. */
    SETTLEMENT
}
