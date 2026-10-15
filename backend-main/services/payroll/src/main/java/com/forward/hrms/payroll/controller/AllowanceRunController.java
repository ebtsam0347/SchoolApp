package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.CreateAllowanceRunRequest;
import com.forward.hrms.payroll.dto.response.AllowanceRunDto;
import com.forward.hrms.payroll.dto.response.AllowanceTransactionDto;
import com.forward.hrms.payroll.dto.response.PayrollRunDto;
import com.forward.hrms.payroll.entity.AllowanceTransaction;
import com.forward.hrms.payroll.enums.AllowanceTransactionStatus;
import com.forward.hrms.payroll.enums.PayslipStatus;
import com.forward.hrms.payroll.service.AllowanceRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Allowance Run", description = "Monthly allowance run lifecycle: DRAFT → LOCKED → COMPUTED → APPROVED → POSTED. Requires `payroll.read` / `payroll.admin`.")

@RestController
@RequestMapping("/allowance-run")
@RequiredArgsConstructor
public class AllowanceRunController {

    private final AllowanceRunService service;

    @Operation(summary = "Create a DRAFT run for a calendar month",
            description = "Requires `payroll.admin`.")
    @PostMapping
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public AllowanceRunDto create(@Valid @RequestBody CreateAllowanceRunRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Get a run by id", description = "Requires `payroll.read`.")
    @GetMapping("/{id}")
    @RequirePermission("payroll.read")
    public AllowanceRunDto get(@PathVariable long id) {
        return service.get(id);
    }

    @Operation(summary = "List runs in a fiscal year", description = "Requires `payroll.read`.")
    @GetMapping
    @RequirePermission("payroll.read")
    public List<AllowanceRunDto> list(@RequestParam("fiscalYear") String fiscalYearCode) {
        return service.listByFiscalYear(fiscalYearCode);
    }

    @Operation(summary = "Get allowance transactions with pagination and filters")
    @GetMapping("/{id}/transactions")
    @RequirePermission("payroll.read")
    public PageResponse<AllowanceTransactionDto> getTransactions(
            @PathVariable("id") Long runId,
            @RequestParam(required = false) AllowanceTransactionStatus status,
            @RequestParam(required = false) String employeeCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Create Pageable WITHOUT sort - let the query's ORDER BY handle it
        Pageable pageable = PageRequest.of(page, size);

        return service.getTransactions(runId, status, employeeCode, pageable);
    }

    @Operation(summary = "Pre-aggregated totals for a run",
            description = "Returns count + sums (allowance) and HELD / RELEASE_PENDING counts as a single row. " +
                    "Optional `status` (PAID / HELD / RELEASE_PENDING / RELEASED) and `q` " +
                    "(case-insensitive `employeeCode` substring) match the same filters as " +
                    "`/allowance`, so the BFF can show a filter-aware rollup alongside the table. " +
                    "Requires `payroll.read`.")
    @GetMapping("/{id}/totals")
    @RequirePermission("payroll.read")
    public com.forward.hrms.payroll.dto.response.AllowanceRunTotalsDto totals(
            @PathVariable long id,
            @Parameter @RequestParam(required = false) AllowanceTransactionStatus status,
            @Parameter @RequestParam(required = false) String q) {
        return service.totals(id, status, q);
    }


    @Operation(summary = "Live run summary for FE polling",
            description = "One call per poll. Returns status + live counts " +
                    "(`totalCount`, `progressCount`, `skippedCount`, `successCount`) and " +
                    "pre-aggregated allowance transaction totals in a single response. Counts pulse every " +
                    "~25 rows; totals come from a SUM over committed allowance transaction so they move " +
                    "live mid-compute. Poll every ~1s while status is `COMPUTING` / `POSTING`; " +
                    "stop polling and load `/allowance-transaction` + `/skips` once status reaches " +
                    "`COMPUTED` / `POSTED`. Requires `payroll.read`.")
    @GetMapping("/{id}/summary")
    @RequirePermission("payroll.read")
    public com.forward.hrms.payroll.dto.response.AllowanceRunSummaryDto summary(@PathVariable long id) {
        return service.summary(id);
    }

    @Operation(summary = "Compute allowance",
            description = "")
    @PostMapping("/{id}/compute")
    @RequirePermission("payroll.admin")
    public AllowanceRunDto compute(@PathVariable long id) {
        return service.compute(id);
    }

    @Operation(summary = "Aprrove a computed run", description = "Requires `payroll.admin`")
    @PostMapping("/{id}/approve")
    @RequirePermission("payroll.admin")
    public AllowanceRunDto approve(@PathVariable long id){
        return service.approve(id);
    }

    @Operation(summary = "Reset a COMPUTED run back to LOCKED",
            description = "Drops every allowance transaction and clear the " +
                    "`computedAt` + `skippedCount`, " +
                    "can fix the employee salary component data and re-run `/compute`. Only valid in `COMPUTED`. Requires " +
                    "`payroll.admin`.")
    @PostMapping("/{id}/reset")
    @RequirePermission("payroll.admin")
    public AllowanceRunDto reset(@PathVariable long id) {
        return service.reset(id);
    }

    @Operation(summary = "Cancel a DRAFT or LOCKED run", description = "Requires `payroll.admin`.")
    @PostMapping("/{id}/cancel")
    @RequirePermission("payroll.admin")
    public AllowanceRunDto cancel(@PathVariable long id) {
        return service.cancel(id);
    }
}
