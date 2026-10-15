package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.ReviseBasicSalaryRequest;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryDto;
import com.forward.hrms.payroll.service.EmployeeSalaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Employee basic salary",
        description = "Per-employee monthly basic. Effective-dated — a revision closes the " +
                "previous current row and inserts a new one. Requires `payroll.read` / `payroll.write`.")
@RestController
@RequestMapping("/salary/employees")
@RequiredArgsConstructor
public class EmployeeSalaryController {

    private final EmployeeSalaryService service;

    @Operation(summary = "Get current basic salary", description = "Requires `payroll.read`.")
    @GetMapping("/{id}")
    @RequirePermission("payroll.read")
    public EmployeeSalaryDto current(@PathVariable("id") long employeeId) {
        return service.current(employeeId);
    }

    @Operation(summary = "Revision history (most recent first)",
            description = "Requires `payroll.read`.")
    @GetMapping("/{id}/history")
    @RequirePermission("payroll.read")
    public List<EmployeeSalaryDto> history(@PathVariable("id") long employeeId) {
        return service.history(employeeId);
    }

    @Operation(summary = "Revise basic salary",
            description = "Closes the existing current row and inserts a new one. HR is " +
                    "expected to revise the INCOME_TAX line on the components endpoint when basic changes." +
                    " Requires `payroll.write`.")
    @PostMapping("/{id}")
    @RequirePermission("payroll.write")
    public EmployeeSalaryDto revise(@PathVariable("id") long employeeId,
                                    @Valid @RequestBody ReviseBasicSalaryRequest req) {
        return service.revise(employeeId, req);
    }
}
