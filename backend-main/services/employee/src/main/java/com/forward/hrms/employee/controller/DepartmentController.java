package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.CreateDepartmentRequest;
import com.forward.hrms.employee.dto.request.UpdateDepartmentRequest;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.service.DepartmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

@Tag(name = "Catalogs — Departments", description = "Top-level org units. Departments are flat — no nesting. Reads are open to any authenticated user (label resolution on the ESS profile screen needs them); writes require `employee.write`.")
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService service;

    @Operation(summary = "List departments",
            description = "Open to any authenticated user — labels feed the ESS profile screen.")
    @GetMapping
    public List<DepartmentDto> list(
            @Parameter(description = "Filter by active flag.") @RequestParam(required = false) Boolean active) {
        return service.list(active);
    }

    @Operation(summary = "Create a department", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Created.")
    @ApiResponse(responseCode = "409", description = "Code already taken.")
    @PostMapping
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentDto create(@Valid @RequestBody CreateDepartmentRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Patch a department",
            description = "Pass `headId=0` to clear the head pointer. Requires `employee.write`.")
    @PatchMapping("/{id}")
    @RequirePermission("employee.write")
    public DepartmentDto update(@PathVariable long id, @Valid @RequestBody UpdateDepartmentRequest req) {
        return service.update(id, req);
    }
}
