package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.ReligionRequest;
import com.forward.hrms.employee.dto.request.UpdateReligionRequest;
import com.forward.hrms.employee.dto.response.ReligionDto;
import com.forward.hrms.employee.service.ReligionService;

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

@Tag(
        name = "Catalogs — Religions",
        description = "Religion lookup catalog. Soft-deactivated, never deleted. Reads are open to any authenticated user; writes require `employee.write`."
)
@RestController
@RequestMapping("/religions")
@RequiredArgsConstructor
public class ReligionController {

    private final ReligionService service;

    @Operation(
            summary = "List religions",
            description = "Open to any authenticated user."
    )
    @GetMapping
    public List<ReligionDto> list(
            @Parameter(description = "Filter by active flag. Omit to return all.")
            @RequestParam(required = false) Boolean active) {
        return service.list(active);
    }

    @Operation(
            summary = "Create a religion",
            description = "Requires `employee.write`."
    )
    @ApiResponse(responseCode = "201", description = "Created.")
    @ApiResponse(responseCode = "409", description = "Religion name already exists.")
    @PostMapping
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public ReligionDto create(@Valid @RequestBody ReligionRequest req) {
        return service.create(req);
    }

    @Operation(
            summary = "Patch a religion",
            description = "Null fields are left unchanged. Use `active=false` to soft-deactivate. Requires `employee.write`."
    )
    @PatchMapping("/{id}")
    @RequirePermission("employee.write")
    public ReligionDto update(
            @PathVariable long id,
            @Valid @RequestBody UpdateReligionRequest req) {
        return service.update(id, req);
    }
}