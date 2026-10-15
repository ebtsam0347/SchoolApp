package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.CreateSalaryComponentRequest;
import com.forward.hrms.payroll.dto.request.ReorderEntryRequest;
import com.forward.hrms.payroll.dto.request.UpdateSalaryComponentRequest;
import com.forward.hrms.payroll.dto.response.SalaryComponentDto;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.service.SalaryComponentService;

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

@Tag(name = "Salary components",
        description = "Catalog of allowance + deduction component types. There is no salary " +
                "structure / template — every employee is `basic` plus a per-employee selection " +
                "from this catalog. Requires `payroll.read` / `payroll.admin`.")
@RestController
@RequestMapping("/salary/components")
@RequiredArgsConstructor
public class SalaryComponentController {

    private final SalaryComponentService service;

    @Operation(summary = "List components", description = "Requires `payroll.read`.")
    @GetMapping
    @RequirePermission("payroll.read")
    public List<SalaryComponentDto> list(
            @Parameter @RequestParam(required = false) ComponentType type,
            @Parameter @RequestParam(required = false) Boolean active) {
        return service.list(type, active);
    }

    @Operation(summary = "Create a component", description = "Requires `payroll.admin`.")
    @PostMapping
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public SalaryComponentDto create(@Valid @RequestBody CreateSalaryComponentRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Update a component (name / default amount / displayOrder / active)",
            description = "Requires `payroll.admin`.")
    @PatchMapping("/{id}")
    @RequirePermission("payroll.admin")
    public SalaryComponentDto update(@PathVariable long id,
                                     @Valid @RequestBody UpdateSalaryComponentRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Bulk-reorder salary components",
            description = "Applies the supplied `displayOrder` to each listed component in a single " +
                    "transaction. Returns the full unfiltered catalog. Requires `payroll.admin`.")
    @PatchMapping("/reorder")
    @RequirePermission("payroll.admin")
    public List<SalaryComponentDto> reorder(@Valid @RequestBody List<ReorderEntryRequest> entries) {
        return service.reorder(entries);
    }
}
