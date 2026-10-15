package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelFuelPolicyRequest;
import com.forward.hrms.employee.dto.request.travel.response.TravelFuelPolicyResponse;
import com.forward.hrms.employee.service.travel.TravelFuelPolicyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Transport Fuel Policy Administration",
        description = "Master fuel/petrol issuance policies Transport applies during travel-request " +
                "verification. Reads require `travel.read`; all mutations require `travel.admin`.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/travel/fuel-policies")
public class TravelFuelPolicyController {

    private final TravelFuelPolicyService service;

    @Operation(summary = "Create a fuel policy", description = "Requires `travel.admin`.")
    @ApiResponse(responseCode = "201", description = "Fuel policy created.")
    @ApiResponse(responseCode = "409", description = "A fuel policy with this code already exists.")
    @PostMapping
    @RequirePermission("travel.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public TravelFuelPolicyResponse create(@Valid @RequestBody CreateTravelFuelPolicyRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Get a fuel policy", description = "Requires `travel.read`.")
    @ApiResponse(responseCode = "404", description = "Fuel policy not found.")
    @GetMapping("/{id}")
    @RequirePermission("travel.read")
    public TravelFuelPolicyResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Search fuel policies (paginated)",
            description = "Both filters optional. Requires `travel.read`.")
    @GetMapping
    @RequirePermission("travel.read")
    public PageResponse<TravelFuelPolicyResponse> search(
            @Parameter(description = "Filter by business code.") @RequestParam(required = false) String code,
            @Parameter(description = "Filter by active flag.") @RequestParam(required = false) Boolean isActive,
            @Parameter(hidden = true) Pageable pageable) {
        return PageResponse.from(service.search(code, isActive, PageableDefaults.apply(pageable, "code")));
    }

    @Operation(summary = "Update a fuel policy", description = "Requires `travel.admin`.")
    @ApiResponse(responseCode = "404", description = "Fuel policy not found.")
    @ApiResponse(responseCode = "409", description = "A fuel policy with the new code already exists.")
    @PatchMapping("/{id}")
    @RequirePermission("travel.admin")
    public TravelFuelPolicyResponse update(@PathVariable Long id,
                                           @Valid @RequestBody CreateTravelFuelPolicyRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Deactivate a fuel policy", description = "Requires `travel.admin`.")
    @ApiResponse(responseCode = "404", description = "Fuel policy not found.")
    @PostMapping("/{id}/deactivate")
    @RequirePermission("travel.admin")
    public TravelFuelPolicyResponse deactivate(@PathVariable Long id) {
        return service.deactivate(id);
    }

    @Operation(summary = "Activate a fuel policy", description = "Requires `travel.admin`.")
    @ApiResponse(responseCode = "404", description = "Fuel policy not found.")
    @PostMapping("/{id}/activate")
    @RequirePermission("travel.admin")
    public TravelFuelPolicyResponse activate(@PathVariable Long id) {
        return service.activate(id);
    }
}
