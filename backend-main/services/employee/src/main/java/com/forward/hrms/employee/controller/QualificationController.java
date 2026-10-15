package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.CreateQualificationRequest;
import com.forward.hrms.employee.dto.request.ReorderEntryRequest;
import com.forward.hrms.employee.dto.request.UpdateQualificationRequest;
import com.forward.hrms.employee.dto.response.QualificationDto;
import com.forward.hrms.employee.service.QualificationService;

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

@Tag(name = "Catalogs — Qualifications",
        description = "Global academic-qualification catalog. Per-employee instances live in /employees/{id}/qualifications. Reads are open to any authenticated user (label resolution on the ESS profile screen needs them); writes require `employee.write`.")
@RestController
@RequestMapping("/qualifications")
@RequiredArgsConstructor
public class QualificationController {

    private final QualificationService service;

    @Operation(summary = "List qualifications",
            description = "Open to any authenticated user — labels feed the ESS profile screen.")
    @GetMapping
    public List<QualificationDto> list(
            @Parameter(description = "Filter by level enum value.")
            @RequestParam(required = false) String level,
            @Parameter(description = "Filter by active flag.")
            @RequestParam(required = false) Boolean active) {
        return service.list(level, active);
    }

    @Operation(summary = "Create a qualification", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Created.")
    @ApiResponse(responseCode = "409", description = "Code already taken.")
    @PostMapping
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public QualificationDto create(@Valid @RequestBody CreateQualificationRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Patch a qualification", description = "Requires `employee.write`.")
    @PatchMapping("/{id}")
    @RequirePermission("employee.write")
    public QualificationDto update(@PathVariable long id, @Valid @RequestBody UpdateQualificationRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Bulk-reorder qualifications",
            description = "Applies the supplied `displayOrder` to each listed qualification in a single " +
                    "transaction. Returns the full unfiltered catalog. Requires `employee.write`.")
    @ApiResponse(responseCode = "404", description = "One or more qualification ids not found.")
    @PatchMapping("/reorder")
    @RequirePermission("employee.write")
    public List<QualificationDto> reorder(@Valid @RequestBody List<ReorderEntryRequest> entries) {
        return service.reorder(entries);
    }
}
