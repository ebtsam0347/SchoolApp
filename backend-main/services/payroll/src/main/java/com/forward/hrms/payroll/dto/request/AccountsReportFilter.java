package com.forward.hrms.payroll.dto.request;

import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.enums.AdvanceBalanceFilter;
import com.forward.hrms.payroll.enums.EmployeeStatusFilter;
import com.forward.hrms.payroll.enums.EmployeeTypeFilter;
import com.forward.hrms.payroll.enums.PaymentModeFilter;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Unified filter for every accounts report. Each report accepts a subset
 * (see {@code AccountsReport.required()/optional()}); fields the report
 * doesn't accept are ignored. Everything the report accepts is AND-combined,
 * and a null field means "not applied".
 */
@Schema(name = "AccountsReportFilter", description = "Filters, paging and sorting for an accounts report run.")
public record AccountsReportFilter(
        @Schema(description = "F1 — fiscal year code.", example = "2026-27", nullable = true)
        String fiscalYearCode,
        @Schema(description = "F2/F3 — salary or advance month, `YYYY-MM`.", example = "2026-09", nullable = true)
        YearMonth month,
        @Schema(description = "F4 — range start (inclusive).", nullable = true)
        LocalDate dateFrom,
        @Schema(description = "F4 — range end (inclusive).", nullable = true)
        LocalDate dateTo,
        @Schema(description = "F5 — as-on date (inclusive).", nullable = true)
        LocalDate asOnDate,
        @Schema(description = "F8 — first card number (inclusive).", example = "100", nullable = true)
        Long cardFrom,
        @Schema(description = "F8 — last card number (inclusive).", example = "250", nullable = true)
        Long cardTo,
        @Schema(description = "F9 — specific card numbers.", nullable = true)
        List<String> cardList,
        @Schema(description = "F12 — department id.", nullable = true)
        Long departmentId,
        @Schema(description = "F13 — section id.", nullable = true)
        Long sectionId,
        @Schema(description = "F14 — designation id.", nullable = true)
        Long designationId,
        @Schema(description = "F15 — employee status.", nullable = true)
        EmployeeStatusFilter empStatus,
        @Schema(description = "F16 — advance balance.", nullable = true)
        AdvanceBalanceFilter balance,
        @Schema(description = "F17 — bonus number within the fiscal year.", example = "1", nullable = true)
        Integer bonusNo,
        @Schema(description = "Free-text match on card number or employee name.", nullable = true)
        String search,
        @Schema(description = "Sort column key (must be a column of the report). Default: card number.",
                example = "balance", nullable = true)
        String sortBy,
        @Schema(description = "`asc` (default) or `desc`.", example = "asc", nullable = true)
        String sortDir,
        @Schema(description = "Zero-based page. Ignored on export.", example = "0", nullable = true)
        Integer page,
        @Schema(description = "Page size, default 50, max 500. Ignored on export.", example = "50", nullable = true)
        Integer size,
        @Schema(description = "F6 — payment mode (employee's current setting).", nullable = true)
        PaymentModeFilter paymentMode,
        @Schema(description = "F7 — production / non-production (by department).", nullable = true)
        EmployeeTypeFilter employeeType,
        @Schema(description = "F11 — bank id (employee's salary bank).", nullable = true)
        Long bankId,
        @Schema(description = "Grade type: MANAGEMENT, SUPERVISOR or LABOUR. Null = all employees.",
                nullable = true)
        GradeType gradeType
) {

    /** Filter without the grade type. */
    public AccountsReportFilter(String fiscalYearCode, YearMonth month, LocalDate dateFrom, LocalDate dateTo,
                                LocalDate asOnDate, Long cardFrom, Long cardTo, List<String> cardList,
                                Long departmentId, Long sectionId, Long designationId,
                                EmployeeStatusFilter empStatus, AdvanceBalanceFilter balance, Integer bonusNo,
                                String search, String sortBy, String sortDir, Integer page, Integer size,
                                PaymentModeFilter paymentMode, EmployeeTypeFilter employeeType, Long bankId) {
        this(fiscalYearCode, month, dateFrom, dateTo, asOnDate, cardFrom, cardTo, cardList, departmentId, sectionId,
                designationId, empStatus, balance, bonusNo, search, sortBy, sortDir, page, size, paymentMode,
                employeeType, bankId, null);
    }

    /** Filter without the salary-only fields (payment mode, employee type, bank). */
    public AccountsReportFilter(String fiscalYearCode, YearMonth month, LocalDate dateFrom, LocalDate dateTo,
                                LocalDate asOnDate, Long cardFrom, Long cardTo, List<String> cardList,
                                Long departmentId, Long sectionId, Long designationId,
                                EmployeeStatusFilter empStatus, AdvanceBalanceFilter balance, Integer bonusNo,
                                String search, String sortBy, String sortDir, Integer page, Integer size) {
        this(fiscalYearCode, month, dateFrom, dateTo, asOnDate, cardFrom, cardTo, cardList, departmentId, sectionId,
                designationId, empStatus, balance, bonusNo, search, sortBy, sortDir, page, size, null, null, null, null);
    }
}
