package com.forward.hrms.payroll.enums;

public enum EmployeeFinalSettlementStatus {

    /**
     * HR has created the settlement, but it is not yet approved.
     */
    DRAFT,

    /**
     * HR Approved & Verified
     */
    HR_APPROVED,

    /**
     * Employee has been paid by Accounts.
     */
    PAID,

    /**
     * Settlement has been rejected.
     */
    REJECTED,

    /**
     * Settlement has been canceled.
     */
    CANCELLED
}