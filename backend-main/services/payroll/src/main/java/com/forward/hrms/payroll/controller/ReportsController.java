package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.core.error.Violation;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.PayRegisterRequest;
import com.forward.hrms.payroll.dto.response.ReportJobDto;
import com.forward.hrms.payroll.entity.ReportJob;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.PayslipLayout;
import com.forward.hrms.payroll.enums.ReportJobStatus;
import com.forward.hrms.payroll.service.AdvancesOutstandingReportService;
import com.forward.hrms.payroll.service.IncomeTaxDeductedReportService;
import com.forward.hrms.payroll.service.IncrementLettersBatchReportService;
import com.forward.hrms.payroll.service.GratuityDatesReportService;
import com.forward.hrms.payroll.service.IncrementListReportService;
import com.forward.hrms.payroll.service.OffCyclePaymentsLogReportService;
import com.forward.hrms.payroll.service.SalaryCertificateService;
import com.forward.hrms.payroll.service.SettlementStatementReportService;
import com.forward.hrms.payroll.service.PayRegisterReportService;
import com.forward.hrms.payroll.service.PayslipPdfService;
import com.forward.hrms.payroll.service.PayslipService;
import com.forward.hrms.payroll.service.PayslipsRunZipReportService;
import com.forward.hrms.payroll.service.ReportJobService;
import com.forward.hrms.payroll.service.StatutoryReportService;
import com.forward.hrms.payroll.service.VarianceReportService;
import com.forward.hrms.payroll.enums.OffCyclePaymentSource;

import com.forward.hrms.payroll.dto.response.PdfUrlResponse;

import io.swagger.v3.oas.annotations.Parameter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Standard payroll report endpoints + the generic async-job lifecycle
 * ({@code /reports/jobs/...}). Sync reports stream the response or
 * return a signed URL; async reports enqueue a {@code report_jobs} row
 * and the FE polls {@code GET /reports/jobs/{id}} until READY.
 */
@Tag(name = "Payroll reports",
        description = "Standard exportable + statutory reports owned by payroll-service. " +
                "Requires `payroll.reports.run`.")
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportsController {

    private final ReportJobService reportJobs;
    private final PayslipService payslips;
    private final PayslipPdfService payslipPdf;
    private final StatutoryReportService statutory;
    private final IncomeTaxDeductedReportService incomeTax;
    private final VarianceReportService variance;
    private final AdvancesOutstandingReportService advances;
    private final OffCyclePaymentsLogReportService offCycleLog;
    private final SalaryCertificateService salaryCertificates;
    private final IncrementListReportService incrementList;
    private final GratuityDatesReportService gratuityDates;
    private final SettlementStatementReportService settlementStatements;

    // ---------- Single payslip (sync) ----------

    @Operation(summary = "Single payslip — signed PDF URL",
            description = "Returns a short-lived MinIO URL for the payslip's PDF. " +
                    "Exposed under /reports/... so the report catalogue routes here; " +
                    "the underlying object is the same one /payslips/{id}/pdf-url returns. " +
                    "Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "404", description = "Payslip or PDF not found.")
    @GetMapping("/payroll/payslips/{id}/pdf-url")
    @RequirePermission("payroll.reports.run")
    public PdfUrlResponse payslipPdfUrl(@PathVariable long id) {
        return payslips.pdfUrl(id);
    }

    @Operation(summary = "All payslips of a run — one PDF",
            description = "Every payslip on the run, one per page, ordered by employee code. Built from the " +
                    "same rows as `GET /payroll/runs/{id}/detail` (employee, bank, earnings, deductions, " +
                    "advances, attendance, leave), so it matches the run-detail screen. Rendered on demand " +
                    "for any run status. `layout=sheet` prints three slips per A4 with cut lines (zero-amount " +
                    "heads left off) for bulk printing; `language=ur` prints the Urdu slip. " +
                    "Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "400", description = "Unknown `layout` or `language`.")
    @ApiResponse(responseCode = "404", description = "Run not found, or it has no payslips.")
    @GetMapping(value = "/payroll/runs/{id}/payslips-pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("payroll.reports.run")
    public ResponseEntity<byte[]> runPayslipsPdf(
            @PathVariable long id,
            @Parameter(description = "`full` (default, one slip per page) or `sheet` (three per A4).", example = "full")
            @RequestParam(defaultValue = "full") String layout,
            @Parameter(description = "`en` (default) or `ur`.", example = "en")
            @RequestParam(defaultValue = "en") String language) {
        boolean urdu = parseUrdu(language);
        byte[] pdf = PayslipLayout.parse(layout) == PayslipLayout.SHEET
                ? payslipPdf.renderSheet(id, null, urdu)
                : payslipPdf.renderPayslips(id, null, urdu);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payslips-run-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ---------- Statutory monthly returns (sync PDF) ----------

    @Operation(summary = "EOBI return — monthly PDF",
            description = "Lists every EOBI-enrolled employee with a non-zero contribution in the named " +
                    "payroll period, with employee code, EOBI registration number, name, CNIC, and " +
                    "contribution amount. Source data for the regulator's portal entry. " +
                    "Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "404", description = "No payroll run for the requested period.")
    @GetMapping(value = "/payroll/eobi-return", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("payroll.reports.run")
    public ResponseEntity<byte[]> eobiReturn(
            @Parameter(description = "Period year, e.g. 2026.") @RequestParam int year,
            @Parameter(description = "Period month 1..12.") @RequestParam int month) {
        byte[] pdf = statutory.renderEobiReturn(year, month);
        String filename = "eobi-return-%d-%02d.pdf".formatted(year, month);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Operation(summary = "PESSI return — monthly PDF",
            description = "Lists every employee with a non-zero employer-PESSI contribution in the period. " +
                    "PESSI is employer-only and does not reduce employee net. Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "404", description = "No payroll run for the requested period.")
    @GetMapping(value = "/payroll/pessi-return", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("payroll.reports.run")
    public ResponseEntity<byte[]> pessiReturn(
            @Parameter(description = "Period year, e.g. 2026.") @RequestParam int year,
            @Parameter(description = "Period month 1..12.") @RequestParam int month) {
        byte[] pdf = statutory.renderPessiReturn(year, month);
        String filename = "pessi-return-%d-%02d.pdf".formatted(year, month);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ---------- Income-tax deducted (sync Excel) ----------

    @Operation(summary = "Income-tax deducted — Excel",
            description = "Per-employee income tax deducted in the named period plus fiscal-year " +
                    "cumulative through the period end. Both bounds must lie inside the same " +
                    "fiscal year. Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "200", description = "XLSX stream.")
    @ApiResponse(responseCode = "409", description = "Period bounds inverted or cross fiscal years.")
    @GetMapping(value = "/payroll/income-tax-deducted",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @RequirePermission("payroll.reports.run")
    public ResponseEntity<StreamingResponseBody> incomeTaxDeducted(
            @Parameter(description = "Period start year.") @RequestParam int fromYear,
            @Parameter(description = "Period start month 1..12.") @RequestParam int fromMonth,
            @Parameter(description = "Period end year (inclusive).") @RequestParam int toYear,
            @Parameter(description = "Period end month 1..12 (inclusive).") @RequestParam int toMonth) {
        String filename = "income-tax-%d-%02d_to_%d-%02d.xlsx".formatted(
                fromYear, fromMonth, toYear, toMonth);
        StreamingResponseBody body = out -> incomeTax.writeXlsx(out, fromYear, fromMonth, toYear, toMonth);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    // ---------- Variance vs previous run (sync Excel) ----------

    @Operation(summary = "Variance vs previous run — Excel",
            description = "Per-employee component-level diff between {@code currentRunId} and " +
                    "either the supplied {@code priorRunId} or the immediate predecessor on the " +
                    "same fiscal year. Only components that changed are listed. " +
                    "Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "200", description = "XLSX stream.")
    @ApiResponse(responseCode = "404", description = "Run not found.")
    @ApiResponse(responseCode = "409", description = "No prior run, or prior == current.")
    @GetMapping(value = "/payroll/variance",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @RequirePermission("payroll.reports.run")
    public ResponseEntity<StreamingResponseBody> variance(
            @Parameter(description = "Current payroll run id.") @RequestParam long currentRunId,
            @Parameter(description = "Optional explicit prior run id; default = same-FY immediate predecessor.")
            @RequestParam(required = false) Long priorRunId) {
        String filename = "variance-run-%d.xlsx".formatted(currentRunId);
        StreamingResponseBody body = out -> variance.writeXlsx(out, currentRunId, priorRunId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    // ---------- Salary certificate (sync PDF) ----------

    @Operation(summary = "Salary certificate — PDF",
            description = "Certificate for banks / embassies: identity, placement, joining date, current fixed " +
                    "monthly salary (basic + flat allowances = gross, in figures and words) and the salary " +
                    "account. Per-day allowances are excluded. Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "404", description = "Employee not found.")
    @ApiResponse(responseCode = "422", description = "EMPLOYEE_INACTIVE (has left) or NO_BASIC_SALARY.")
    @GetMapping(value = "/payroll/employees/{id}/salary-certificate", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("payroll.reports.run")
    public ResponseEntity<byte[]> salaryCertificate(
            @PathVariable long id,
            @Parameter(description = "Addressee line, e.g. \"The Branch Manager, Meezan Bank, Sialkot\". Default: To Whom It May Concern.")
            @RequestParam(required = false) @Size(max = 200) String addressee,
            @Parameter(description = "Purpose, e.g. \"car loan application\". Printed in the closing line.")
            @RequestParam(required = false) @Size(max = 200) String purpose) {
        byte[] pdf = salaryCertificates.render(id, addressee, purpose);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"salary-certificate-%d.pdf\"".formatted(id))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ---------- Final settlement statement (sync PDF) ----------

    @Operation(summary = "Final settlement statement — PDF",
            description = "Employee's dues statement at the end of job for one final settlement: service details, " +
                    "payments | deductions, net payable in figures and words, office signatures and the " +
                    "employee's acknowledgement. Printed from the stored settlement snapshot; a DRAFT / " +
                    "REJECTED / CANCELLED settlement prints with a red status chip. Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "404", description = "FINAL_SETTLEMENT_NOT_FOUND.")
    @GetMapping(value = "/payroll/settlements/{id}/statement", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequirePermission("payroll.reports.run")
    public ResponseEntity<byte[]> settlementStatement(@PathVariable long id) {
        byte[] pdf = settlementStatements.render(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"settlement-statement-%d.pdf\"".formatted(id))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ---------- Salary increment list (sync Excel) ----------

    @Operation(summary = "Salary increment list — Excel",
            description = "Every basic-salary revision effective in the period (default: current month): previous " +
                    "and new basic, increment in rupees and percent, reason. Optional department. " +
                    "Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "200", description = "XLSX stream.")
    @ApiResponse(responseCode = "422", description = "INVALID_PERIOD — `from` after `to`.")
    @GetMapping(value = "/payroll/increments",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @RequirePermission("payroll.reports.run")
    public ResponseEntity<StreamingResponseBody> incrementList(
            @Parameter(description = "Effective from (inclusive). Default: first of this month.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Effective to (inclusive). Default: end of `from`'s month.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Department id.") @RequestParam(required = false) Long departmentId) {
        // Load on the request thread (identity for any employee-service call, audit in a tx); stream the write.
        IncrementListReportService.Loaded loaded = incrementList.load(from, to, departmentId);
        StreamingResponseBody body = streamAsCaller(out -> incrementList.write(out, loaded));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"salary-increments-%s.xlsx\""
                        .formatted(from == null ? LocalDate.now().toString().substring(0, 7) : from.toString()))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    // ---------- Joining vs gratuity date (sync Excel) ----------

    @Operation(summary = "Joining vs gratuity date — Excel",
            description = "Active employees on payroll with their joining date beside the date gratuity is counted " +
                    "from (gratuity start date, else payroll hire date). By default only those where the two differ. " +
                    "Optional department. Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "200", description = "XLSX stream.")
    @GetMapping(value = "/payroll/gratuity-dates",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @RequirePermission("payroll.reports.run")
    public ResponseEntity<StreamingResponseBody> gratuityDates(
            @Parameter(description = "Only employees whose gratuity date differs from joining (default true).")
            @RequestParam(defaultValue = "true") boolean onlyDifferent,
            @Parameter(description = "Department id.") @RequestParam(required = false) Long departmentId) {
        GratuityDatesReportService.Loaded loaded = gratuityDates.load(onlyDifferent, departmentId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"gratuity-dates-%s.xlsx\"".formatted(LocalDate.now()))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(streamAsCaller(out -> gratuityDates.write(out, loaded)));
    }

    /** Streaming bodies run on an async thread with no inbound request; carry the caller over for "Generated by". */
    private static StreamingResponseBody streamAsCaller(StreamingResponseBody body) {
        CurrentUser caller = CurrentUserHolder.get().orElse(null);
        return out -> {
            if (caller != null) CurrentUserHolder.set(caller);
            try {
                body.writeTo(out);
            } finally {
                if (caller != null) CurrentUserHolder.clear();
            }
        };
    }

    // ---------- Salary advances outstanding (sync Excel) ----------

    @Operation(summary = "Salary advances outstanding — Excel",
            description = "Two sheets: open permanent advances (APPROVED with remaining balance > 0) " +
                    "and approved short-term advances not yet deducted in any payroll run. " +
                    "Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "200", description = "XLSX stream.")
    @GetMapping(value = "/payroll/advances-outstanding",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @RequirePermission("payroll.reports.run")
    public ResponseEntity<StreamingResponseBody> advancesOutstanding() {
        String filename = "advances-outstanding-" + java.time.LocalDate.now() + ".xlsx";
        StreamingResponseBody body = out -> advances.writeXlsx(out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    // ---------- Off-cycle payments log (sync Excel) ----------

    @Operation(summary = "Off-cycle payments log — Excel",
            description = "Bonuses, ad-hoc adjustments, leave encashments, and any other payments " +
                    "outside the regular monthly run, filtered by date range and optional " +
                    "transaction source. `source` is the internal classification (HR / BONUS / " +
                    "FINAL_SETTLEMENT), not the HR-facing payment type. " +
                    "Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "200", description = "XLSX stream.")
    @ApiResponse(responseCode = "409", description = "Range invalid (from > to or missing).")
    @GetMapping(value = "/payroll/off-cycle-payments",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @RequirePermission("payroll.reports.run")
    public ResponseEntity<StreamingResponseBody> offCyclePayments(
            @Parameter(description = "Window start (yyyy-mm-dd, inclusive).") @RequestParam java.time.LocalDate from,
            @Parameter(description = "Window end (yyyy-mm-dd, inclusive).") @RequestParam java.time.LocalDate to,
            @Parameter(description = "Optional transaction-source filter.")
            @RequestParam(required = false) OffCyclePaymentSource source) {
        String filename = "off-cycle-payments-%s_to_%s.xlsx".formatted(from, to);
        StreamingResponseBody body = out -> offCycleLog.writeXlsx(out, from, to, source);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    // ---------- Increment letters batch (async ZIP) ----------

    @Operation(summary = "Increment letters batch — enqueue ZIP of per-employee PDFs",
            description = "Renders one A4 increment letter per employee in the named increment " +
                    "batch and packages the bundle as a ZIP. Poll {@code GET /reports/jobs/{id}}; " +
                    "once READY, download via {@code /reports/jobs/{id}/download}. " +
                    "Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "201", description = "Job queued.")
    @PostMapping("/payroll/increment-letters/jobs")
    @RequirePermission("payroll.reports.run")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportJobDto enqueueIncrementLetters(
            @Parameter(description = "Increment-batch id (POSTED).") @RequestParam long incrementBatchId) {
        ReportJob job = reportJobs.enqueue(IncrementLettersBatchReportService.TYPE,
                Map.of("incrementBatchId", incrementBatchId));
        return ReportJobDto.of(job);
    }

    // ---------- Payslips whole-run zip (async ZIP) ----------

    @Operation(summary = "Payslips whole-run — enqueue ZIP of every payslip PDF",
            description = "Zips every payslip PDF that {@code PayslipPdfService.generateAll} has " +
                    "already written to MinIO for the named run. Skips payslips with no " +
                    "{@code pdf_object_key}. Throws if the run has no generated PDFs at all " +
                    "(finalize the run first). Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "201", description = "Job queued.")
    @PostMapping("/payroll/payslips-run/jobs")
    @RequirePermission("payroll.reports.run")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportJobDto enqueuePayslipsRun(
            @Parameter(description = "Payroll run id.") @RequestParam long payrollRunId) {
        ReportJob job = reportJobs.enqueue(PayslipsRunZipReportService.TYPE,
                Map.of("payrollRunId", payrollRunId));
        return ReportJobDto.of(job);
    }

    // ---------- Pay register (async) ----------

    @Operation(summary = "Pay register — enqueue an async Excel render",
            description = "Enqueues a {@code PAY_REGISTER} job; the runner renders the Excel and " +
                    "uploads to MinIO. Poll {@code GET /reports/jobs/{id}} for status; once READY, " +
                    "{@code GET /reports/jobs/{id}/download} returns a 302 to a signed URL. " +
                    "Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "201", description = "Job queued.")
    @PostMapping("/payroll/pay-register/jobs")
    @RequirePermission("payroll.reports.run")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportJobDto enqueuePayRegister(@Valid @RequestBody PayRegisterRequest req) {
        ReportJob job = reportJobs.enqueue(PayRegisterReportService.TYPE,
                Map.of("payrollRunId", req.payrollRunId()));
        return ReportJobDto.of(job);
    }

    // ---------- Generic job lifecycle ----------

    @Operation(summary = "Read a report job's status",
            description = "Single source of truth for the FE's poll loop. Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "404", description = "Job not found.")
    @GetMapping("/jobs/{id}")
    @RequirePermission("payroll.reports.run")
    public ReportJobDto get(@PathVariable long id) {
        return ReportJobDto.of(reportJobs.get(id));
    }

    @Operation(summary = "Download a READY report",
            description = "Returns a 302 to a short-lived signed MinIO URL. The FE follows it " +
                    "automatically, so the response never exposes the bucket. Throws 409 if the " +
                    "job is still QUEUED / RUNNING / FAILED. Requires `payroll.reports.run`.")
    @ApiResponse(responseCode = "302", description = "Redirect to signed URL.")
    @ApiResponse(responseCode = "409", description = "Job is not READY.")
    @GetMapping("/jobs/{id}/download")
    @RequirePermission("payroll.reports.run")
    public ResponseEntity<Void> download(@PathVariable long id) {
        ReportJob job = reportJobs.get(id);
        if (job.getStatus() != ReportJobStatus.READY || job.getOutputKey() == null) {
            throw new ConflictError(PayrollErrorCode.REPORT_JOB_NOT_READY,
                    "report job " + id + " is " + job.getStatus());
        }
        String url = reportJobs.signedDownloadUrl(job);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }

    private static boolean parseUrdu(String language) {
        return switch (language.trim().toLowerCase(Locale.ROOT)) {
            case "en" -> false;
            case "ur" -> true;
            default -> throw new ValidationError(PayrollErrorCode.REPORT_PARAM_INVALID, "unsupported language",
                    List.of(new Violation("language", "INVALID", "en or ur")));
        };
    }
}
