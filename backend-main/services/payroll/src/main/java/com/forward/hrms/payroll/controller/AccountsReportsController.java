package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.core.error.Violation;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.dto.response.AccountsReportDefinitionDto;
import com.forward.hrms.payroll.dto.response.AccountsReportResultDto;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.PayslipLayout;
import com.forward.hrms.payroll.enums.ReportExportFormat;
import com.forward.hrms.payroll.service.AccountsDocumentService;
import com.forward.hrms.payroll.service.AccountsReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * Reports → Account Reports. One registry, one data endpoint and one export
 * endpoint for every report; the report id picks the columns, filters and
 * data source. POST because the filter carries a card list.
 */
@Tag(name = "Accounts reports",
        description = "Legacy WebHRMS accounts reports (salary, advances, bonus, allowances, statutory). "
                + "Registry-driven: `GET /registry` lists each report's filters and columns. "
                + "Requires `payroll.reports.run`.")
@RestController
@RequestMapping("/reports/payroll/accounts")
@RequiredArgsConstructor
public class AccountsReportsController {

    private final AccountsReportService reports;
    private final AccountsDocumentService documents;

    @Operation(summary = "Accounts report registry",
            description = "Every available accounts report with its group, viewer type, accepted filters "
                    + "(required / optional) and columns in display order. The FE builds its filter panel "
                    + "and table from this. Requires `payroll.reports.run`.")
    @GetMapping("/registry")
    @RequirePermission("payroll.reports.run")
    public List<AccountsReportDefinitionDto> registry() {
        return reports.registry();
    }

    @Operation(summary = "Run an accounts report — one page",
            description = "Rows for the requested page plus grand totals and employee count over the full "
                    + "filtered result. Filters the report doesn't accept are ignored; the ones it accepts are "
                    + "AND-combined. Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "400", description = "Missing required filter, inverted card/date range, "
            + "unknown sort column.")
    @ApiResponse(responseCode = "404", description = "Unknown report id.")
    @ApiResponse(responseCode = "502", description = "employee-service lookup failed.")
    @PostMapping("/{reportId}/data")
    @RequirePermission("payroll.reports.run")
    public AccountsReportResultDto data(
            @Parameter(description = "Legacy report id.", example = "C2") @PathVariable String reportId,
            @RequestBody AccountsReportFilter filter) {
        return reports.data(reportId, filter);
    }

    @Operation(summary = "Export an accounts report",
            description = "The full filtered result (paging ignored) as Excel or CSV, with Sr No, the "
                    + "applied filters, printed-by line and a grand-total row. Same rows and totals as the "
                    + "data endpoint for the same filters. Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "200", description = "File download.")
    @ApiResponse(responseCode = "400", description = "Invalid filter or format.")
    @ApiResponse(responseCode = "404", description = "Unknown report id.")
    @PostMapping("/{reportId}/export")
    @RequirePermission("payroll.reports.run")
    public ResponseEntity<byte[]> export(
            @Parameter(description = "Legacy report id.", example = "C2") @PathVariable String reportId,
            @Parameter(description = "`xlsx` or `csv`.", example = "xlsx") @RequestParam(defaultValue = "xlsx")
            String format,
            @RequestBody AccountsReportFilter filter) {
        AccountsReportService.Exported file = reports.export(reportId, filter, parseFormat(format));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.body());
    }

    @Operation(summary = "Print an accounts report document (PDF)",
            description = "Slips (A1 salary, B3 temporary advance, E1 bonus — two half-page slips per A4 for B3/E1), "
                    + "bank transfer letters (A6, B6, E5 — addressed to the chosen bank, debiting the company "
                    + "account configured on it), the C5 permanent advance ledger (one card) and the F-7 allowances "
                    + "calculation sheet (Legal landscape, one column per allowance). Registry `printable` marks "
                    + "these reports. Printed from the "
                    + "same filtered rows as the data endpoint. `language=ur` prints the A1 salary slip in Urdu; "
                    + "other documents are English only. `layout=sheet` prints A1 slips three per A4 with cut lines "
                    + "(zero-amount heads left off) for bulk printing; `full` (default) is one slip per page. Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "200", description = "PDF download.")
    @ApiResponse(responseCode = "400", description = "Invalid filter, or the report has no PDF (`printable` false).")
    @ApiResponse(responseCode = "404", description = "Unknown report id, or nothing to print for these filters.")
    @ApiResponse(responseCode = "422", description = "Letter bank has no company account configured; "
            + "ledger filters match more than one employee.")
    @PostMapping(value = "/{reportId}/document", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("payroll.reports.run")
    public ResponseEntity<byte[]> document(
            @Parameter(description = "Legacy report id.", example = "A6") @PathVariable String reportId,
            @Parameter(description = "`en` (default) or `ur` (A1 salary slip only).", example = "en")
            @RequestParam(defaultValue = "en") String language,
            @Parameter(description = "`full` (default, one slip per page) or `sheet` (three per A4; A1 only).",
                    example = "full")
            @RequestParam(defaultValue = "full") String layout,
            @RequestBody AccountsReportFilter filter) {
        AccountsReportService.Exported file = documents.render(reportId, filter, parseLanguage(language),
                PayslipLayout.parse(layout) == PayslipLayout.SHEET);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(file.body());
    }

    private static AccountsDocumentService.Language parseLanguage(String language) {
        try {
            return AccountsDocumentService.Language.valueOf(language.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationError(PayrollErrorCode.ACCOUNTS_REPORT_FILTER_INVALID, "unsupported language",
                    List.of(new Violation("language", "INVALID", "en or ur")));
        }
    }

    private static ReportExportFormat parseFormat(String format) {
        try {
            return ReportExportFormat.valueOf(format.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationError(PayrollErrorCode.ACCOUNTS_REPORT_FILTER_INVALID, "unsupported format",
                    List.of(new Violation("format", "INVALID", "xlsx or csv")));
        }
    }
}
