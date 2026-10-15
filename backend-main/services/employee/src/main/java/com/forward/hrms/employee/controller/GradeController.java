package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.CreateGradeRequest;
import com.forward.hrms.employee.dto.request.ReorderEntryRequest;
import com.forward.hrms.employee.dto.request.UpdateGradeRequest;
import com.forward.hrms.employee.dto.response.GradeDto;
import com.forward.hrms.employee.service.GradeService;

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

@Tag(name = "Catalogs — Grades", description = "Seniority grades. `level` orders rows; higher = more senior. Reads are open to any authenticated user (label resolution on the ESS profile screen needs them); writes require `employee.write`.")
@RestController
@RequestMapping("/grades")
@RequiredArgsConstructor
public class GradeController {

    private final GradeService service;

    @Operation(summary = "List grades",
            description = "Open to any authenticated user — labels feed the ESS profile screen.")
    @GetMapping
    public List<GradeDto> list(
            @Parameter(description = "Filter by active flag.") @RequestParam(required = false) Boolean active) {
        return service.list(active);
    }

    @Operation(summary = "Create a grade", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Created.")
    @ApiResponse(responseCode = "409", description = "Code already taken.")
    @PostMapping
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public GradeDto create(@Valid @RequestBody CreateGradeRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Patch a grade", description = "Requires `employee.write`.")
    @PatchMapping("/{id}")
    @RequirePermission("employee.write")
    public GradeDto update(@PathVariable long id, @Valid @RequestBody UpdateGradeRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Bulk-reorder grades",
            description = "Applies the supplied `displayOrder` to each listed grade in a single " +
                    "transaction. Rows not present in the payload keep their current order. " +
                    "Returns the full catalog (post-reorder). Requires `employee.write`.")
    @ApiResponse(responseCode = "404", description = "One or more grade ids not found.")
    @PatchMapping("/reorder")
    @RequirePermission("employee.write")
    public List<GradeDto> reorder(@Valid @RequestBody List<ReorderEntryRequest> entries) {
        return service.reorder(entries);
    }
}
