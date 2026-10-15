package com.forward.hrms.payroll.controller.composite;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.BonusEventDetail;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.EmployeeSalaryTab;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.IncrementBatchDetail;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.MyPayslipsResponse;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.OffCyclePaymentRowWithEmployee;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayrollRunDetail;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayrollRunsListResponse;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipQueueResponse;
import com.forward.hrms.payroll.enums.OffCyclePaymentSource;
import com.forward.hrms.payroll.enums.PayslipStatus;
import com.forward.hrms.payroll.service.composite.PayrollCompositesService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * FE-facing composite endpoints for payroll surfaces. Each method
 * pairs payroll-internal data with employee-summary enrichment from
 * the shared cache, replacing what was previously assembled in the
 * BFF's {@code PayrollBffController}. Paths are sibling to the
 * existing slim endpoints (suffixed {@code /detail}, {@code
 * /with-summary}, {@code /with-employee}, etc.) so service-to-service
 * callers of the slim shapes are unaffected.
 *
 * <p>Permission gates mirror the prior BFF surface — payroll's own
 * {@code @PreAuthorize("isAuthenticated()")} for the self-serve
 * payslip read, {@code payroll.read} / {@code payroll.admin} for the
 * HR-facing surfaces.
 */
@Tag(name = "Payroll — Composites",
        description = "Composite read endpoints — server-side employee resolution and run " +
                "rollups so the FE renders names and totals without fan-out.")
@RestController
@RequiredArgsConstructor
public class PayrollCompositesController {

    private final PayrollCompositesService service;

    @Operation(summary = "Self-serve payslips for the caller, by year",
            description = "Resolves the caller's employee id server-side, fetches the year's " +
                    "payslips, and mints a short-lived signed PDF URL per row when generated.")
    @GetMapping("/payslips/me")
    @PreAuthorize("isAuthenticated()")
    public MyPayslipsResponse myPayslips(
            @Parameter(description = "Calendar year, e.g. 2026.") @RequestParam int year,
            @Parameter(hidden = true) Pageable pageable) {
        return service.myPayslips(year, PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Run detail with server-side rollup + employee enrichment",
            description = "Returns the run, a summary across all payslips matching the filters, " +
                    "and a paginated slice already enriched with employee name + code. " +
                    "Filters: `status` (PAID/HELD/RELEASE_PENDING/RELEASED) and `q` " +
                    "(case-insensitive `employeeCode` substring). Requires `payroll.read`.")
    @GetMapping("/payroll/runs/{id}/detail")
    @RequirePermission("payroll.read")
    public PayrollRunDetail runDetail(
            @PathVariable long id,
            @Parameter @RequestParam(required = false) PayslipStatus status,
            @Parameter(description = "Substring on employeeCode.")
            @RequestParam(required = false) String q,
            @Parameter(hidden = true) Pageable pageable) {
        return service.runDetail(id, status, q, PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Runs in a fiscal year, each with a server-side rollup",
            description = "Lists every run for the given fiscal year and computes the per-run " +
                    "summary (employees / gross / net / tax / EOBI / employer PESSI). Requires `payroll.read`.")
    @GetMapping("/payroll/runs/with-summary")
    @RequirePermission("payroll.read")
    public PayrollRunsListResponse runsWithSummary(
            @Parameter(description = "Fiscal year code, e.g. FY26.")
            @RequestParam("fiscalYear") String fiscalYearCode) {
        return service.runsForFiscalYear(fiscalYearCode);
    }

    @Operation(summary = "Increment batch with employee enrichment",
            description = "Returns the batch + lines, each line resolved to a name + employee code. " +
                    "Requires `payroll.read`.")
    @GetMapping("/salary/increment-batches/{id}/detail")
    @RequirePermission("payroll.read")
    public IncrementBatchDetail incrementBatch(@PathVariable long id) {
        return service.incrementBatch(id);
    }

    @Operation(summary = "Bonus event with employee enrichment",
            description = "Returns the event + frozen amounts per employee with names resolved. " +
                    "Requires `payroll.read`.")
    @GetMapping("/bonuses/events/{id}/detail")
    @RequirePermission("payroll.read")
    public BonusEventDetail bonusEvent(@PathVariable long id) {
        return service.bonusEvent(id);
    }

    @Operation(summary = "Off-cycle payments enriched with employee names",
            description = "Forwards the upstream filters (`employeeId`, `from`, `to`, `source`) " +
                    "and resolves each row's employee server-side. Requires `payroll.read`.")
    @GetMapping("/off-cycle-payments/with-employee")
    @RequirePermission("payroll.read")
    public PageResponse<OffCyclePaymentRowWithEmployee> offCyclePaymentsWithEmployee(
            @Parameter @RequestParam(required = false) Long employeeId,
            @Parameter @RequestParam(required = false) LocalDate from,
            @Parameter @RequestParam(required = false) LocalDate to,
            @Parameter(description = "Transaction source — HR, BONUS or FINAL_SETTLEMENT.")
            @RequestParam(required = false) OffCyclePaymentSource source,
            @Parameter(hidden = true) Pageable pageable) {
        return service.offCyclePaymentsWithEmployee(employeeId, from, to, source,
                PageableDefaults.apply(pageable, "paidOn"));
    }

    @Operation(summary = "Employee salary tab, one shot",
            description = "Bundles current basic, basic history, effective component lines, the " +
                    "active component catalog, payroll settings, bank details (or null), gratuity, " +
                    "and the last 12 months of off-cycle payments. Replaces 8 fan-out calls. " +
                    "Requires `payroll.read`.")
    @GetMapping("/payroll/people/{id}/salary")
    @RequirePermission("payroll.read")
    public EmployeeSalaryTab salaryTab(@PathVariable long id) {
        return service.salaryTab(id);
    }

    @Operation(summary = "Disbursement queue for HELD / RELEASE_PENDING payslips",
            description = "Page of payslips at the given status with employee names + run period " +
                    "merged in. Requires `payroll.admin`.")
    @GetMapping("/payslips/queue")
    @RequirePermission("payroll.admin")
    public PayslipQueueResponse payslipQueue(
            @Parameter(description = "HELD / RELEASE_PENDING / RELEASED / PAID")
            @RequestParam PayslipStatus status,
            @Parameter(hidden = true) Pageable pageable) {
        return service.payslipQueue(status, PageableDefaults.apply(pageable, "id"));
    }
}
