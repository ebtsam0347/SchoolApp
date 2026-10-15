package com.forward.hrms.employee.enums.travel;

public enum TravelSettlementStatus {

    DRAFT,

    SUBMITTED,

    /**
     * HR has completed the HR Settlement Verification stage; the Accounts
     * Payment Confirmation stage is now pending.
     */
    HR_APPROVED,

    UNDER_REVIEW,

    APPROVED,

    PARTIALLY_APPROVED,

    REJECTED,

    PAYMENT_PENDING,

    RECOVERY_PENDING,

    PAID,

    RECOVERED,

    SETTLED,

    CANCELLED
}
