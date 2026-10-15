package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.CreateCityRequest;
import com.forward.hrms.employee.dto.request.ReorderEntryRequest;
import com.forward.hrms.employee.dto.request.UpdateCityRequest;
import com.forward.hrms.employee.dto.response.CityDto;
import com.forward.hrms.employee.service.CityService;

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

@Tag(name = "Catalogs — Cities", description = "Pakistan cities catalog (scoped to a province). Reads are open to any authenticated user (label resolution on the ESS profile screen needs them); writes require `employee.write`.")
@RestController
@RequestMapping("/cities")
@RequiredArgsConstructor
public class CityController {

    private final CityService service;

    @Operation(summary = "List cities",
            description = "Filter by province and / or active flag. Open to any authenticated user — labels feed the ESS profile screen.")
    @GetMapping
    public List<CityDto> list(
            @Parameter(description = "Restrict to a single province.") @RequestParam(required = false) Long provinceId,
            @Parameter(description = "Filter by active flag.") @RequestParam(required = false) Boolean active) {
        return service.list(provinceId, active);
    }

    @Operation(summary = "Create a city", description = "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Created.")
    @ApiResponse(responseCode = "404", description = "Province not found.")
    @ApiResponse(responseCode = "409", description = "Code already taken or referenced province inactive.")
    @PostMapping
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public CityDto create(@Valid @RequestBody CreateCityRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Patch a city", description = "Requires `employee.write`.")
    @PatchMapping("/{id}")
    @RequirePermission("employee.write")
    public CityDto update(@PathVariable long id, @Valid @RequestBody UpdateCityRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Bulk-reorder cities",
            description = "Applies the supplied `displayOrder` to each listed city in a single " +
                    "transaction. Returns the full unfiltered catalog. Requires `employee.write`.")
    @ApiResponse(responseCode = "404", description = "One or more city ids not found.")
    @PatchMapping("/reorder")
    @RequirePermission("employee.write")
    public List<CityDto> reorder(@Valid @RequestBody List<ReorderEntryRequest> entries) {
        return service.reorder(entries);
    }
}
