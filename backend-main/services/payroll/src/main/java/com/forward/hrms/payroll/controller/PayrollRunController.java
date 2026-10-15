package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.CreatePayrollRunRequest;
import com.forward.hrms.payroll.dto.request.RetrySkipsRequest;
import com.forward.hrms.payroll.dto.request.SetAdvanceWaiverRequest;
import com.forward.hrms.payroll.dto.response.PayrollRunDto;
import com.forward.hrms.payroll.dto.response.PayrollRunSkipDto;
import com.forward.hrms.payroll.dto.response.PayslipDto;
import com.forward.hrms.payroll.dto.response.RunSkipAdvanceDto;
import com.forward.hrms.payroll.enums.PayslipStatus;
import com.forward.hrms.payroll.service.PayrollRunService;
import com.forward.hrms.payroll.service.PayrollRunUndoService;
import com.forward.hrms.payroll.service.PayslipPdfService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Payroll runs",
        description = "Monthly run lifecycle: DRAFT → LOCKED → COMPUTED → APPROVED → POSTED. Requires `payroll.read` / `payroll.admin`.")
@RestController
@RequestMapping("/payroll/runs")
@RequiredArgsConstructor
public class PayrollRunController {

    private final PayrollRunService service;
    private final PayslipPdfService pdfService;
    private final PayrollRunUndoService undoService;

    @Operation(summary = "Create a DRAFT run for a calendar month",
            description = "Requires `payroll.admin`.")
    @PostMapping
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollRunDto create(@Valid @RequestBody CreatePayrollRunRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Get a run by id", description = "Requires `payroll.read`.")
    @GetMapping("/{id}")
    @RequirePermission("payroll.read")
    public PayrollRunDto get(@PathVariable long id) {
        return service.get(id);
    }

    @Operation(summary = "List runs in a fiscal year", description = "Requires `payroll.read`.")
    @GetMapping
    @RequirePermission("payroll.read")
    public List<PayrollRunDto> list(@RequestParam("fiscalYear") String fiscalYearCode) {
        return service.listByFiscalYear(fiscalYearCode);
    }

    @Operation(summary = "Payslips on a run (paginated, filterable)",
            description = "Optional filters: `status` (PAID / HELD / RELEASE_PENDING / RELEASED) and " +
                    "`q` (case-insensitive substring on `employeeCode`). Requires `payroll.read`.")
    @GetMapping("/{id}/payslips")
    @RequirePermission("payroll.read")
    public PageResponse<PayslipDto> payslips(@PathVariable long id,
                                             @Parameter @RequestParam(required = false) PayslipStatus status,
                                             @Parameter @RequestParam(required = false) String q,
                                             @Parameter(hidden = true) Pageable pageable) {
        return service.payslips(id, status, q, PageableDefaults.apply(pageable, "employeeCode"));
    }

    @Operation(summary = "Pre-aggregated totals for a run",
            description = "Returns count + sums (basic, allowances, gross, deductions, tax, eobi, " +
                    "pessiEmployer, net) and HELD / RELEASE_PENDING counts as a single row. " +
                    "Optional `status` (PAID / HELD / RELEASE_PENDING / RELEASED) and `q` " +
                    "(case-insensitive `employeeCode` substring) match the same filters as " +
                    "`/payslips`, so the BFF can show a filter-aware rollup alongside the table. " +
                    "Requires `payroll.read`.")
    @GetMapping("/{id}/totals")
    @RequirePermission("payroll.read")
    public com.forward.hrms.payroll.dto.response.PayrollRunTotalsDto totals(
            @PathVariable long id,
            @Parameter @RequestParam(required = false) PayslipStatus status,
            @Parameter @RequestParam(required = false) String q) {
        return service.totals(id, status, q);
    }

    @Operation(summary = "Live run summary for FE polling",
            description = "One call per poll. Returns status + live counts " +
                    "(`totalCount`, `progressCount`, `skippedCount`, `successCount`) and " +
                    "pre-aggregated payslip totals in a single response. Counts pulse every " +
                    "~25 rows; totals come from a SUM over committed payslips so they move " +
                    "live mid-compute. Poll every ~1s while status is `COMPUTING` / `POSTING`; " +
                    "stop polling and load `/payslips` + `/skips` once status reaches " +
                    "`COMPUTED` / `POSTED`. Requires `payroll.read`.")
    @GetMapping("/{id}/summary")
    @RequirePermission("payroll.read")
    public com.forward.hrms.payroll.dto.response.PayrollRunSummaryDto summary(@PathVariable long id) {
        return service.summary(id);
    }

    @Operation(summary = "Lock attendance for the run's period",
            description = "Verifies the period is already locked in attendance (the operator closes " +
                    "the month from the attendance side) and flips the run DRAFT → LOCKED. Does not " +
                    "command the lock: 409 if the attendance period isn't locked yet — close it in " +
                    "attendance first. Requires `payroll.admin`.")
    @PostMapping("/{id}/lock-attendance")
    @RequirePermission("payroll.admin")
    public PayrollRunDto lockAttendance(@PathVariable long id) {
        return service.lockAttendance(id);
    }

    @Operation(summary = "Compute payslips",
            description = "Builds payslips for every active employee against the locked attendance " +
                    "period. To re-compute after fixing data, use `/reset` first. Requires `payroll.admin`.")
    @PostMapping("/{id}/compute")
    @RequirePermission("payroll.admin")
    public PayrollRunDto compute(@PathVariable long id) {
        return service.compute(id);
    }

    @Operation(summary = "Reset a COMPUTED run back to LOCKED",
            description = "Drops every payslip and skip row on the run (payslip_lines cascade) and clears " +
                    "`computedAt` + `skippedCount`, leaving the attendance period locked so the operator " +
                    "can fix employee data and re-run `/compute`. Only valid in `COMPUTED`. Requires " +
                    "`payroll.admin`.")
    @PostMapping("/{id}/reset")
    @RequirePermission("payroll.admin")
    public PayrollRunDto reset(@PathVariable long id) {
        return service.reset(id);
    }

    @Operation(summary = "List employees skipped during compute",
            description = "Each row carries the actionable reason (`errorCode` + `message`) and the " +
                    "snapshot employee code/name so the FE can render and deeplink without an extra " +
                    "lookup. Requires `payroll.read`.")
    @GetMapping("/{id}/skips")
    @RequirePermission("payroll.read")
    public PageResponse<PayrollRunSkipDto> skips(@PathVariable long id,
                                                 @Parameter(hidden = true) Pageable pageable) {
        return service.listSkips(id, PageableDefaults.apply(pageable, "employeeCode"));
    }

    @Operation(summary = "Re-run compute for skipped employees",
            description = "After HR fixes the underlying data (salary, bank details, etc.), this " +
                    "re-runs compute for the listed skip rows (or every row if the body is empty). " +
                    "Resolved employees lose their skip row and gain a payslip; rows that still " +
                    "fail are updated with the new reason. Run stays `COMPUTED`. Requires " +
                    "`payroll.admin`.")
    @PostMapping("/{id}/retry-skips")
    @RequirePermission("payroll.admin")
    public PayrollRunDto retrySkips(@PathVariable long id,
                                    @RequestBody(required = false) RetrySkipsRequest req) {
        return service.retrySkips(id, req == null ? null : req.employeeIds());
    }

    @Operation(summary = "Undo one payslip on a COMPUTED run",
            description = "Deletes the DRAFT payslip (lines cascade) together with the \"From Salary\" RFD row " +
                    "and the permanent-advance deduction its compute wrote, and adds the employee to the " +
                    "run's skip list (`errorCode = MANUALLY_UNDONE`). Fix the data, then bring them back " +
                    "with `/retry-skips`. HELD payslips must be unheld first. Requires `payroll.admin`.")
    @ApiResponse(responseCode = "404", description = "Run not found, or payslip not on this run")
    @ApiResponse(responseCode = "409", description = "Run is not COMPUTED, or payslip is not DRAFT")
    @PostMapping("/{id}/payslips/{payslipId}/undo")
    @RequirePermission("payroll.admin")
    public PayrollRunDto undoPayslip(@PathVariable long id, @PathVariable long payslipId) {
        return undoService.undoPayslip(id, payslipId);
    }

    @Operation(summary = "Running permanent advance of a skipped employee",
            description = "The latest APPROVED permanent advance compute deducts from, with the outstanding " +
                    "balance. `advanceId` is null when nothing is outstanding. Requires `payroll.read`.")
    @ApiResponse(responseCode = "404", description = "Run not found, or employee not skipped on it")
    @GetMapping("/{id}/skips/{employeeId}/permanent-advance")
    @RequirePermission("payroll.read")
    public RunSkipAdvanceDto skipAdvance(@PathVariable long id, @PathVariable long employeeId) {
        return undoService.skipAdvance(id, employeeId);
    }

    @Operation(summary = "Waive a skipped employee's permanent-advance installment for the run's month",
            description = "`waived: true` makes `/retry-skips` (and any recompute of this month, even after a " +
                    "reset) deduct 0 and write a 0 ledger row. The advance's monthly installment is not changed, " +
                    "so next month deducts as usual. Run must be COMPUTED. Requires `payroll.admin`.")
    @ApiResponse(responseCode = "404", description = "Run not found, or employee not skipped on it")
    @ApiResponse(responseCode = "409", description = "Run is not COMPUTED")
    @ApiResponse(responseCode = "422", description = "Waiving with no running advance")
    @PutMapping("/{id}/skips/{employeeId}/permanent-advance/waiver")
    @RequirePermission("payroll.admin")
    public RunSkipAdvanceDto setSkipAdvanceWaiver(@PathVariable long id, @PathVariable long employeeId,
                                                  @Valid @RequestBody SetAdvanceWaiverRequest req) {
        return undoService.setSkipAdvanceWaiver(id, employeeId, req.waived());
    }

    @Operation(summary = "Preview a skipped employee's payslip",
            description = "Runs the same compute as `/retry-skips` for this one employee and rolls it back, so " +
                    "nothing is saved. Reflects the current waiver. Compute errors (e.g. no basic salary) come " +
                    "back as they would on retry. Requires `payroll.read`.")
    @ApiResponse(responseCode = "404", description = "Run not found, or employee not skipped on it")
    @GetMapping("/{id}/skips/{employeeId}/preview")
    @RequirePermission("payroll.read")
    public PayslipDto previewSkip(@PathVariable long id, @PathVariable long employeeId) {
        return undoService.previewSkip(id, employeeId);
    }

    @Operation(summary = "Approve a computed run", description = "Requires `payroll.admin`.")
    @PostMapping("/{id}/approve")
    @RequirePermission("payroll.admin")
    public PayrollRunDto approve(@PathVariable long id) {
        return service.approve(id);
    }

    @Operation(summary = "Post — generates PDFs and emits per-employee events. Irreversible.",
            description = "Requires `payroll.admin`.")
    @PostMapping("/{id}/post")
    @RequirePermission("payroll.admin")
    public PayrollRunDto post(@PathVariable long id) {
        return service.post(id);
    }

    @Operation(summary = "Regenerate missing payslip PDFs of a POSTED run",
            description = "Repair for a posted run whose payslips have no PDF in MinIO (e.g. posted while " +
                    "uploads were failing). Re-renders every payslip with no PDF, or whose recorded object is " +
                    "missing from the bucket; payslips whose PDF exists are left alone, so it is idempotent. " +
                    "Synchronous. Returns the run summary. Requires `payroll.admin`.")
    @ApiResponse(responseCode = "404", description = "Run not found")
    @ApiResponse(responseCode = "409", description = "Run is not POSTED")
    @PostMapping("/{id}/regenerate-pdfs")
    @RequirePermission("payroll.admin")
    public com.forward.hrms.payroll.dto.response.PayrollRunSummaryDto regeneratePdfs(
            @PathVariable long id,
            @Parameter(description = "Also re-render PDFs that exist, overwriting them with the current " +
                    "template. No event, no employee notification.")
            @RequestParam(defaultValue = "false") boolean force) {
        pdfService.regenerateMissing(id, force);
        return service.summary(id);
    }

    @Operation(summary = "Cancel a DRAFT or LOCKED run", description = "Requires `payroll.admin`.")
    @PostMapping("/{id}/cancel")
    @RequirePermission("payroll.admin")
    public PayrollRunDto cancel(@PathVariable long id) {
        return service.cancel(id);
    }
}
