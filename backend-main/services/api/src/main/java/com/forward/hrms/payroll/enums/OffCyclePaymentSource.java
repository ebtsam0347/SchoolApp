package com.forward.hrms.payroll.enums;

/**
 * Where an {@code off_cycle_payments} row came from — the internal
 * transaction classification, stored in the legacy {@code category}
 * column.
 *
 * <p><strong>This is not the HR-facing type list.</strong> The types HR
 * picks from live in employee-service's {@code off_cycle_payment_type}
 * master, which HR maintains, and the selection is stored on the payment
 * as {@code offCyclePaymentTypeId} plus a name snapshot. The two are
 * deliberately separate: HR renaming or deactivating a master type must
 * never change how a payment is classified financially.
 *
 * <p>In particular, an HR-created payment is {@link #HR} <em>whatever the
 * selected type is called</em>. A master type named "Bonus" or "Special
 * Bonus" does not produce a {@link #BONUS} row — {@code BONUS} is
 * reserved for bonus-event installments, which is what
 * {@code SettlementBonusCalculator} consumes. Letting the master name
 * decide would hand HR control of settlement math.
 */
public enum OffCyclePaymentSource {

    /** HR recorded it and picked a type from the employee-service master. */
    HR,

    /**
     * Written by {@code BonusEventService.applyInstallment} via
     * {@code OffCyclePaymentService.recordBonusInstallment}, and by
     * nothing else. Consumed by {@code SettlementBonusCalculator}.
     */
    BONUS,

    /**
     * Written by the settlement engine when closing a leaver (currently
     * dormant). System-generated; not an HR-selectable type.
     */
    FINAL_SETTLEMENT;

    /** True for rows HR created, the only ones carrying a master type. */
    public boolean isHrCreated() {
        return this == HR;
    }
}
