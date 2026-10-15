package com.forward.hrms.payroll.enums;

/**
 * Filters an accounts report can take. {@code legacyCode} is the F-number
 * from the WebHRMS accounts-reports spec, kept so accounts staff and the
 * mapping doc can cross-reference. Branch (F10) is absent on purpose —
 * employees carry no branch column. Slip Language (F18) is presentation
 * only: it travels as the {@code language} query parameter of the document
 * endpoint, not on the filter body.
 */
public enum AccountsReportParam {
    FISCAL_YEAR("F1"),
    /** Salary month (F2) or advance month (F3) — one {@code YearMonth} either way. */
    MONTH("F2/F3"),
    DATE_RANGE("F4"),
    AS_ON_DATE("F5"),
    PAYMENT_MODE("F6"),
    EMPLOYEE_TYPE("F7"),
    CARD_RANGE("F8"),
    CARD_LIST("F9"),
    BANK("F11"),
    DEPARTMENT("F12"),
    SECTION("F13"),
    DESIGNATION("F14"),
    EMP_STATUS("F15"),
    BALANCE("F16"),
    BONUS_NO("F17"),
    SLIP_LANGUAGE("F18"),
    SHIFT("F19"),
    /** Grade's role bucket (management / supervisor / labour). No legacy F-number — the legacy system
     *  had a separate "Management" sheet instead. */
    GRADE_TYPE(null);

    private final String legacyCode;

    AccountsReportParam(String legacyCode) {
        this.legacyCode = legacyCode;
    }

    public String legacyCode() {
        return legacyCode;
    }
}
