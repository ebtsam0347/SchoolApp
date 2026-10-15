package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.SetAllowancesRequest;
import com.forward.hrms.payroll.dto.response.AllowanceColumnDto;
import com.forward.hrms.payroll.dto.response.AllowanceHistoryEntryDto;
import com.forward.hrms.payroll.dto.response.EmployeeAllowanceRowDto;
import com.forward.hrms.payroll.dto.response.EmployeeAllowancesDto;
import com.forward.hrms.payroll.service.EmployeeAllowanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Employee allowances",
        description = "Flat monthly allowances paid apart from salary (catalog components with " +
                "calculateWithSalary = false), read and written as one set per employee. Stored as " +
                "effective-dated employee salary component lines; the allowance run pays the open ones. " +
                "Requires `payroll.read` / `payroll.write`.")
@RestController
@RequestMapping("/allowances")
@RequiredArgsConstructor
public class EmployeeAllowanceController {

    private final EmployeeAllowanceService service;

    @Operation(summary = "Allowance columns",
            description = "Active allowance components paid apart from salary, in display order. Requires `payroll.read`.")
    @GetMapping("/columns")
    @RequirePermission("payroll.read")
    public List<AllowanceColumnDto> columns() {
        return service.columns();
    }

    @Operation(summary = "Current allowances of every employee",
            description = "One row per employee with at least one open allowance line, ordered by card no. " +
                    "`q` matches name, card no, department or section (case-insensitive). Requires `payroll.read`.")
    @GetMapping("/employees")
    @RequirePermission("payroll.read")
    public PageResponse<EmployeeAllowanceRowDto> employees(
            @Parameter(description = "Name, card no, department or section.") @RequestParam(required = false) String q,
            Pageable pageable) {
        return service.employees(q, PageableDefaults.apply(pageable, PageableDefaults.DEFAULT_SIZE,
                PageableDefaults.MAX_SIZE, Sort.unsorted()));
    }

    @Operation(summary = "One employee's current allowances", description = "Requires `payroll.read`.")
    @ApiResponse(responseCode = "404", description = "Employee not found")
    @GetMapping("/employees/{id}")
    @RequirePermission("payroll.read")
    public EmployeeAllowancesDto forEmployee(@PathVariable("id") long employeeId) {
        return service.forEmployee(employeeId);
    }

    @Operation(summary = "Versions of one employee's allowance set, newest first",
            description = "Requires `payroll.read`.")
    @ApiResponse(responseCode = "404", description = "Employee not found")
    @GetMapping("/employees/{id}/history")
    @RequirePermission("payroll.read")
    public List<AllowanceHistoryEntryDto> history(@PathVariable("id") long employeeId) {
        return service.history(employeeId);
    }

    @Operation(summary = "Replace one employee's allowance set as of today",
            description = "Changed amounts close the open line today and open a new one today; 0 or an absent " +
                    "code closes that allowance today; unchanged amounts are left alone. Every save is its own " +
                    "History version. Requires `payroll.write`.")
    @ApiResponse(responseCode = "204", description = "Saved")
    @ApiResponse(responseCode = "404", description = "Employee not found")
    @ApiResponse(responseCode = "422", description = "Unknown or non-allowance component code")
    @PutMapping("/employees/{id}")
    @RequirePermission("payroll.write")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void set(@PathVariable("id") long employeeId, @Valid @RequestBody SetAllowancesRequest req) {
        service.set(employeeId, req);
    }
}
