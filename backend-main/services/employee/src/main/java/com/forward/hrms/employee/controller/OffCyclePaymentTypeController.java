package com.forward.hrms.employee.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.OffCyclePaymentTypeRequest;
import com.forward.hrms.employee.dto.request.UpdateOffCyclePaymentType;
import com.forward.hrms.employee.dto.response.OffCyclePaymentTypeDto;
import com.forward.hrms.employee.service.OffCyclePaymentTypeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(
        name = "Catalogs — Off Cycle Payment types",
        description = "Employee Off Cycle Payment types catalog. Soft-deactivated, never deleted."
)
@RestController
@RequestMapping("/employees/off-cycle-payment-types")
@RequiredArgsConstructor
public class OffCyclePaymentTypeController {

    private final OffCyclePaymentTypeService service;

    @GetMapping
    @Operation(summary = "List off cycle payment types")
    public List<OffCyclePaymentTypeDto> list(
            @Parameter(description = "Filter by active flag")
            @RequestParam(required = false) Boolean active) {
        return service.list(active);
    }

    @PostMapping
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create off cycle payment type")
    @ApiResponse(responseCode = "201", description = "Created")
    public OffCyclePaymentTypeDto create(
            @Valid @RequestBody OffCyclePaymentTypeRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}")
    @RequirePermission("employee.write")
    @Operation(summary = "Update off cycle payment type")
    public OffCyclePaymentTypeDto update(
            @PathVariable long id,
            @Valid @RequestBody UpdateOffCyclePaymentType req) {
        return service.update(id, req);
    }
}