package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.HoldPayslipRequest;
import com.forward.hrms.payroll.dto.response.PayslipDto;
import com.forward.hrms.payroll.dto.response.PdfUrlResponse;
import com.forward.hrms.payroll.enums.PayslipStatus;
import com.forward.hrms.payroll.service.PayslipService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payslips",
        description = "Per-employee payslip reads, hold/release lifecycle, signed PDF URLs. Requires `payroll.read` / `payroll.admin`.")
@RestController
@RequiredArgsConstructor
public class PayslipController {

    private final PayslipService service;

    @Operation(summary = "Get a payslip with line breakdown",
            description = "Visible to `payroll.read` / `payroll.admin` (HR / ADMIN / ACCOUNTS — any " +
                    "employee, all statuses), the owning employee (self-serve, posted only), and a " +
                    "manager holding `payroll.read.team` when the payslip's employee is a direct " +
                    "report (posted only). A DRAFT returns 404 to restricted callers.")
    @ApiResponse(responseCode = "404", description = "Payslip not found (or DRAFT, to a restricted caller).")
    @GetMapping("/payslips/{id}")
    public PayslipDto get(@PathVariable long id) {
        return service.get(id);
    }

    @Operation(summary = "List payslips for an employee + year (paginated)",
            description = "Self-serve when the path id matches the caller's employee id (resolved " +
                    "via employee-service /me). `payroll.read` / `payroll.admin` callers may read " +
                    "any employee (incl. DRAFT); a manager with `payroll.read.team` may read a " +
                    "direct report (posted only). Default sort is `periodMonth desc`.")
    @GetMapping("/payslips/employees/{id}")
    public PageResponse<PayslipDto> forEmployeeYear(@PathVariable("id") long employeeId,
                                                    @Parameter @RequestParam int year,
                                                    @Parameter(hidden = true) Pageable pageable) {
        return service.listForEmployeeYear(employeeId, year, pageable);
    }

    @Operation(summary = "Manager: list my team's payslips (paginated)",
            description = "The calling manager's direct reports' payslips for a year (optionally a " +
                    "single month). Scope is resolved server-side via employee-service /me/team; " +
                    "DRAFT is hidden and a caller with no reports gets an empty page. Requires " +
                    "`payroll.read.team` (managers) or `payroll.read` (HR / ADMIN / ACCOUNTS). " +
                    "Default sort is `periodMonth desc`.")
    @ApiResponse(responseCode = "403", description = "Caller holds neither payroll.read.team nor payroll.read.")
    @GetMapping("/payslips/team")
    public PageResponse<PayslipDto> team(@Parameter @RequestParam int year,
                                         @Parameter @RequestParam(required = false) Integer month,
                                         @Parameter(hidden = true) Pageable pageable) {
        return service.listForTeam(year, month, pageable);
    }

    @Operation(summary = "Short-lived signed MinIO URL for the PDF",
            description = "Same access rules as GET /payslips/{id}: `payroll.read` / `payroll.admin`, " +
                    "the owning employee, or a manager (`payroll.read.team`) over a direct report.")
    @GetMapping("/payslips/{id}/pdf-url")
    public PdfUrlResponse pdfUrl(@PathVariable long id) {
        return service.pdfUrl(id);
    }

    @Operation(summary = "List payslips by status (HELD / RELEASE_PENDING / RELEASED / PAID)",
            description = "Requires `payroll.read`.")
    @GetMapping("/payslips")
    @RequirePermission("payroll.read")
    public PageResponse<PayslipDto> filter(@RequestParam PayslipStatus status,
                                           @Parameter(hidden = true) Pageable pageable) {
        return service.filterByStatus(status, PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Hold a payslip (DRAFT or PAID → HELD)",
            description = "Pre-post on a DRAFT payslip the post step skips the row; post-post on a " +
                    "PAID payslip the operator can later `release` in a future run. Requires `payroll.admin`.")
    @PostMapping("/payslips/{id}/hold")
    @RequirePermission("payroll.admin")
    public PayslipDto hold(@PathVariable long id, @Valid @RequestBody HoldPayslipRequest req) {
        return service.hold(id, req);
    }

    @Operation(summary = "Unhold (HELD → DRAFT). Only valid before the run is POSTED.",
            description = "Returns the payslip to the review state so the run's post step can " +
                    "promote it to PAID. Requires `payroll.admin`.")
    @PostMapping("/payslips/{id}/unhold")
    @RequirePermission("payroll.admin")
    public PayslipDto unhold(@PathVariable long id, @Valid @RequestBody HoldPayslipRequest req) {
        return service.unhold(id, req);
    }

    @Operation(summary = "Release a held payslip (HELD in POSTED run → RELEASE_PENDING).",
            description = "The next compute picks this up and adds a HELD_SALARY_RELEASE earning " +
                    "line to that employee's new payslip. Requires `payroll.admin`.")
    @PostMapping("/payslips/{id}/release")
    @RequirePermission("payroll.admin")
    public PayslipDto release(@PathVariable long id, @Valid @RequestBody HoldPayslipRequest req) {
        return service.release(id, req);
    }
}
