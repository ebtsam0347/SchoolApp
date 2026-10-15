package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.AddSalaryLineRequest;
import com.forward.hrms.payroll.dto.request.RemoveSalaryLineRequest;
import com.forward.hrms.payroll.dto.request.UpdateSalaryLineRequest;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryComponentDto;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.service.EmployeeSalaryComponentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Employee salary components",
        description = "Per-employee allowance + deduction lines. Single endpoint family for both; " +
                "type comes from the catalog row. Requires `payroll.read` / `payroll.write`.")
@RestController
@RequestMapping("/employees/{id}/salary-components")
@RequiredArgsConstructor
public class EmployeeSalaryComponentController {

    private final EmployeeSalaryComponentService service;

    @Operation(summary = "Current effective lines", description = "Requires `payroll.read`.")
    @GetMapping
    @RequirePermission("payroll.read")
    public List<EmployeeSalaryComponentDto> list(
            @PathVariable("id") long employeeId,
            @Parameter @RequestParam(required = false) ComponentType type) {
        return service.listCurrent(employeeId, type);
    }

    @Operation(summary = "Full history (most recent first)",
            description = "Requires `payroll.read`.")
    @GetMapping("/history")
    @RequirePermission("payroll.read")
    public List<EmployeeSalaryComponentDto> history(@PathVariable("id") long employeeId) {
        return service.history(employeeId);
    }

    @Operation(summary = "Add a line",
            description = "Picks an active row from the salary-components catalog. amount " +
                    "defaults to component.defaultAmount when omitted. Requires `payroll.write`.")
    @PostMapping
    @RequirePermission("payroll.write")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeSalaryComponentDto add(@PathVariable("id") long employeeId,
                                          @Valid @RequestBody AddSalaryLineRequest req) {
        return service.add(employeeId, req);
    }

    @Operation(summary = "Update a line — closes the current row and inserts a new one",
            description = "Requires `payroll.write`.")
    @PatchMapping("/{lineId}")
    @RequirePermission("payroll.write")
    public EmployeeSalaryComponentDto update(@PathVariable("id") long employeeId,
                                             @PathVariable long lineId,
                                             @Valid @RequestBody UpdateSalaryLineRequest req) {
        return service.update(employeeId, lineId, req);
    }

    @Operation(summary = "Stop a line",
            description = "Sets effective_to on the current row. Requires `payroll.write`.")
    @PostMapping("/{lineId}/stop")
    @RequirePermission("payroll.write")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable("id") long employeeId, @PathVariable long lineId,
                       @Valid @RequestBody RemoveSalaryLineRequest req) {
        service.remove(employeeId, lineId, req);
    }
}
