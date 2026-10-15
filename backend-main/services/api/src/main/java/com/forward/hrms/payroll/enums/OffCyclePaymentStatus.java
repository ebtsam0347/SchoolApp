package com.forward.hrms.payroll.enums;

/**
 * Lifecycle of an {@code off_cycle_payments} row. Two authorities, three
 * states — HR creates (which <em>is</em> the HR authorization; there is no
 * separate submit/approve step), then either HR cancels or Accounts pays.
 *
 * <pre>
 * CREATE ──▶ HR_APPROVED ──┬── HR edit ──▶ HR_APPROVED
 *                          ├── HR cancel ─▶ CANCELLED  (immutable)
 *                          └── Accounts pay ▶ PAID     (immutable)
 * </pre>
 *
 * No other transition is permitted. Rows created before the lifecycle
 * existed are backfilled to {@link #PAID} (payroll V0056) because they
 * were recorded as completed payment facts.
 */
public enum OffCyclePaymentStatus {

    /** HR has created and authorized it; Accounts has not paid it yet. */
    HR_APPROVED,

    /** Accounts has executed the payment. Terminal and immutable. */
    PAID,

    /** HR canceled it before payment. Terminal and immutable. */
    CANCELLED;

    /** True while HR may still edit, cancel, or Accounts may still pay. */
    public boolean isOpen() {
        return this == HR_APPROVED;
    }
}
