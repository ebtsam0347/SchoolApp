package com.forward.hrms.payroll.enums;

/** Menu groups under Reports → Account Reports, in legacy order. */
public enum AccountsReportGroup {
    SALARY("Salary"),
    TEMPORARY_ADVANCE("Temporary Advance"),
    PERMANENT_ADVANCE("Permanent Advance"),
    RFD_FAIR_PRICE("RFD & Fair Price"),
    BONUS("Bonus"),
    ALLOWANCES("Allowances"),
    DEDUCTIONS_STATUTORY("Deductions & Statutory"),
    TRANSACTION_VIEWS("Transaction Views");

    private final String label;

    AccountsReportGroup(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
