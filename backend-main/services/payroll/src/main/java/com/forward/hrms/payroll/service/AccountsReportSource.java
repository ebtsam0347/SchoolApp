package com.forward.hrms.payroll.service;

import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.dto.response.AccountsReportColumnDto;
import com.forward.hrms.payroll.enums.AccountsReport;

import java.util.List;

/**
 * Payroll-side data for a family of accounts reports — one implementation
 * per data source (permanent advances, short-term advances, payslips, …),
 * not per report. Applies the report's period and fixed conditions only;
 * employee filters are applied afterwards by {@link AccountsReportService}.
 * Rows come back in the order the report should print them within one
 * employee (the service sorts employees by card, stably).
 */
public interface AccountsReportSource {

    boolean supports(AccountsReport report);

    List<AccountsReportRow> rows(AccountsReport report, AccountsReportFilter filter);

    /**
     * The report's columns in display order, without Sr No. Override when
     * they depend on data (e.g. one column per salary component).
     */
    default List<AccountsReportColumnDto> columns(AccountsReport report) {
        return report.columns();
    }

    /** Readable captions for filters only this source can name (e.g. the bank). */
    default List<String> captionParts(AccountsReport report, AccountsReportFilter filter) {
        return List.of();
    }
}
