package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.CreateProvinceRequest;
import com.forward.hrms.employee.dto.request.UpdateProvinceRequest;
import com.forward.hrms.employee.dto.response.ProvinceDto;
import com.forward.hrms.employee.service.ProvinceService;

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

@Tag(name = "Catalogs — Provinces", description = "Pakistan provinces / federal areas. Soft-deactivated, never deleted. Reads are open to any authenticated user (label resolution on the ESS profile screen needs them); writes require `employee.write`.")
@RestController
@RequestMapping("/provinces")
@RequiredArgsConstructor
public class ProvinceController {

    private final ProvinceService service;

    @Operation(summary = "List provinces",
            description = "Open to any authenticated user — labels feed the ESS profile screen.")
    @GetMapping
    public List<ProvinceDto> list(
            @Parameter(description = "Filter by active flag. Omit to return all.")
            @RequestParam(required = false) Boolean active) {
        return service.list(active);
    }

    @Operation(summary = "Create a province", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Created.")
    @ApiResponse(responseCode = "409", description = "Code already taken.")
    @PostMapping
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public ProvinceDto create(@Valid @RequestBody CreateProvinceRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Patch a province",
            description = "Null fields are left unchanged. Use `active=false` to soft-deactivate. Requires `employee.write`.")
    @PatchMapping("/{id}")
    @RequirePermission("employee.write")
    public ProvinceDto update(@PathVariable long id, @Valid @RequestBody UpdateProvinceRequest req) {
        return service.update(id, req);
    }
}
