package com.forward.hrms.employee.enums.travel;

/**
 * Represents the lifecycle state of a Travel Request advance.
 *
 * <p>
 * The status represents the business state of the advance,
 * from initial creation through approval, payment and final
 * settlement.
 * </p>
 */
public enum TravelAdvanceStatus {

    /**
     * Advance has been created but has not yet been submitted.
     */
    DRAFT,

    /**
     * Advance has been submitted for processing/approval.
     */
    SUBMITTED,

    /**
     * Advance is currently being reviewed.
     */
    UNDER_REVIEW,

    /**
     * Advance has been fully approved.
     */
    APPROVED,

    /**
     * Requested amount was approved only partially.
     */
    PARTIALLY_APPROVED,

    /**
     * Advance request was rejected.
     */
    REJECTED,

    /**
     * Advance has been approved and is waiting for payment.
     */
    PAYMENT_PENDING,

    /**
     * Advance payment has been released to the employee.
     */
    PAID,

    /**
     * Advance has been canceled.
     */
    CANCELED,

    /**
     * Advance has been completely reconciled/settled.
     */
    SETTLED
}
