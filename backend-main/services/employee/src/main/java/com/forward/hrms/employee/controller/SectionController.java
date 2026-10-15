package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.CreateSectionRequest;
import com.forward.hrms.employee.dto.request.UpdateSectionRequest;
import com.forward.hrms.employee.dto.response.SectionDto;
import com.forward.hrms.employee.service.SectionService;

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

@Tag(name = "Catalogs — Sections",
        description = "Sections are scoped to a single department. Used as the secondary org unit when a department is too coarse. Reads are open to any authenticated user (label resolution on the ESS profile screen needs them); writes require `employee.write`.")
@RestController
@RequestMapping("/sections")
@RequiredArgsConstructor
public class SectionController {

    private final SectionService service;

    @Operation(summary = "List sections",
            description = "Filter by department and / or active flag. Open to any authenticated user — labels feed the ESS profile screen.")
    @GetMapping
    public List<SectionDto> list(
            @Parameter(description = "Restrict to a single department.")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "Filter by active flag.")
            @RequestParam(required = false) Boolean active) {
        return service.list(departmentId, active);
    }

    @Operation(summary = "Create a section", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Created.")
    @ApiResponse(responseCode = "404", description = "Parent department not found.")
    @ApiResponse(responseCode = "409", description = "Code already taken or department inactive.")
    @PostMapping
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public SectionDto create(@Valid @RequestBody CreateSectionRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Patch a section",
            description = "Pass `headId=0` to clear the head pointer. Requires `employee.write`.")
    @PatchMapping("/{id}")
    @RequirePermission("employee.write")
    public SectionDto update(@PathVariable long id, @Valid @RequestBody UpdateSectionRequest req) {
        return service.update(id, req);
    }
}
