package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.CreateSkillRequest;
import com.forward.hrms.employee.dto.request.UpdateSkillRequest;
import com.forward.hrms.employee.dto.response.SkillDto;
import com.forward.hrms.employee.service.SkillService;

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

@Tag(name = "Catalogs — Skills", description = "Global skill catalog. Per-employee proficiency lives elsewhere. Reads are open to any authenticated user (label resolution on the ESS profile screen needs them); writes require `employee.write`.")
@RestController
@RequestMapping("/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService service;

    @Operation(summary = "List skills",
            description = "Open to any authenticated user — labels feed the ESS profile screen.")
    @GetMapping
    public List<SkillDto> list(
            @Parameter(description = "Filter by category — TECH | LANGUAGE | TRADE | SPORT | OTHER.")
            @RequestParam(required = false) String category,
            @Parameter(description = "Filter by active flag.")
            @RequestParam(required = false) Boolean active) {
        return service.list(category, active);
    }

    @Operation(summary = "Create a skill", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Created.")
    @ApiResponse(responseCode = "409", description = "Code already taken.")
    @PostMapping
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public SkillDto create(@Valid @RequestBody CreateSkillRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Patch a skill", description = "Requires `employee.write`.")
    @PatchMapping("/{id}")
    @RequirePermission("employee.write")
    public SkillDto update(@PathVariable long id, @Valid @RequestBody UpdateSkillRequest req) {
        return service.update(id, req);
    }
}
