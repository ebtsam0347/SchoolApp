package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.core.error.Violation;
import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.dto.response.AccountsReportColumnDto;
import com.forward.hrms.payroll.enums.AccountsReport;
import com.forward.hrms.payroll.enums.AccountsReportOutput;
import com.forward.hrms.payroll.enums.AccountsReportParam;
import com.forward.hrms.payroll.enums.PayrollErrorCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Backend validation of an accounts report filter against the report's
 * registry entry: required filters present, card and date ranges ordered,
 * sort column known. Collects every violation so the FE can mark all bad
 * fields at once.
 */
public final class AccountsReportFilterValidator {

    private AccountsReportFilterValidator() {}

    /** {@code columns}: the report's columns as shown (Sr No included), for the sort check. */
    public static void validate(AccountsReport r, AccountsReportFilter f, List<AccountsReportColumnDto> columns) {
        List<Violation> v = new ArrayList<>();
        for (AccountsReportParam p : r.required()) {
            if (!present(p, f)) {
                v.add(new Violation(field(p), "REQUIRED",
                        (p.legacyCode() == null ? p.name() : p.legacyCode()) + " is required for " + r.id()));
            }
        }
        if (r.accepts(AccountsReportParam.CARD_RANGE)) {
            if (f.cardFrom() != null && f.cardFrom() <= 0) v.add(new Violation("cardFrom", "POSITIVE", "must be > 0"));
            if (f.cardTo() != null && f.cardTo() <= 0) v.add(new Violation("cardTo", "POSITIVE", "must be > 0"));
            if (f.cardFrom() != null && f.cardTo() != null && f.cardFrom() > f.cardTo()) {
                v.add(new Violation("cardFrom", "RANGE", "cardFrom must be <= cardTo"));
            }
        }
        if (r.accepts(AccountsReportParam.DATE_RANGE) && f.dateFrom() != null && f.dateTo() != null
                && f.dateFrom().isAfter(f.dateTo())) {
            v.add(new Violation("dateFrom", "RANGE", "dateFrom must be <= dateTo"));
        }
        if (r.accepts(AccountsReportParam.BONUS_NO) && f.bonusNo() != null && f.bonusNo() <= 0) {
            v.add(new Violation("bonusNo", "POSITIVE", "must be > 0"));
        }
        if (r.output() == AccountsReportOutput.LEDGER && f.cardList() != null && f.cardList().size() > 1) {
            v.add(new Violation("cardList", "SINGLE", "a ledger is printed for one card at a time"));
        }
        if (f.sortBy() != null && !f.sortBy().isBlank()
                && columns.stream().noneMatch(c -> c.key().equals(f.sortBy()))) {
            v.add(new Violation("sortBy", "UNKNOWN", "not a column of " + r.id()));
        }
        if (f.sortDir() != null && !f.sortDir().equalsIgnoreCase("asc") && !f.sortDir().equalsIgnoreCase("desc")) {
            v.add(new Violation("sortDir", "INVALID", "asc or desc"));
        }
        if (!v.isEmpty()) {
            throw new ValidationError(PayrollErrorCode.ACCOUNTS_REPORT_FILTER_INVALID,
                    "invalid filter for " + r.id(), v);
        }
    }

    private static boolean present(AccountsReportParam p, AccountsReportFilter f) {
        return switch (p) {
            case FISCAL_YEAR -> f.fiscalYearCode() != null && !f.fiscalYearCode().isBlank();
            case MONTH -> f.month() != null;
            case DATE_RANGE -> f.dateFrom() != null && f.dateTo() != null;
            case AS_ON_DATE -> f.asOnDate() != null;
            case CARD_RANGE -> f.cardFrom() != null && f.cardTo() != null;
            case CARD_LIST -> f.cardList() != null && f.cardList().stream().anyMatch(c -> c != null && !c.isBlank());
            case DEPARTMENT -> f.departmentId() != null;
            case SECTION -> f.sectionId() != null;
            case DESIGNATION -> f.designationId() != null;
            case EMP_STATUS -> f.empStatus() != null;
            case BALANCE -> f.balance() != null;
            case BONUS_NO -> f.bonusNo() != null;
            case PAYMENT_MODE -> f.paymentMode() != null;
            case EMPLOYEE_TYPE -> f.employeeType() != null;
            case BANK -> f.bankId() != null;
            case GRADE_TYPE -> f.gradeType() != null;
            // Not on the filter record: SHIFT waits for A3; SLIP_LANGUAGE is a query parameter.
            case SHIFT, SLIP_LANGUAGE -> false;
        };
    }

    private static String field(AccountsReportParam p) {
        return switch (p) {
            case FISCAL_YEAR -> "fiscalYearCode";
            case MONTH -> "month";
            case DATE_RANGE -> "dateFrom";
            case AS_ON_DATE -> "asOnDate";
            case CARD_RANGE -> "cardFrom";
            case CARD_LIST -> "cardList";
            case DEPARTMENT -> "departmentId";
            case SECTION -> "sectionId";
            case DESIGNATION -> "designationId";
            case EMP_STATUS -> "empStatus";
            case BALANCE -> "balance";
            case BONUS_NO -> "bonusNo";
            case PAYMENT_MODE -> "paymentMode";
            case EMPLOYEE_TYPE -> "employeeType";
            case BANK -> "bankId";
            case GRADE_TYPE -> "gradeType";
            case SHIFT -> "shift";
            case SLIP_LANGUAGE -> "language";
        };
    }
}
