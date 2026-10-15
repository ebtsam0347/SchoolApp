package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.CreateDisabilityRequest;
import com.forward.hrms.employee.dto.request.UpdateDisabilityRequest;
import com.forward.hrms.employee.dto.response.DisabilityDto;
import com.forward.hrms.employee.service.DisabilityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@Tag(name = "Disabilities",
        description = "Disability category catalog (PWD ordinance compliance). Read for everyone, " +
                "CRUD for HR. Requires `employee.read` / `employee.admin`.")
@RestController
@RequestMapping("/disabilities")
@RequiredArgsConstructor
public class DisabilityController {

    private final DisabilityService service;

    @Operation(summary = "List disabilities",
            description = "Active only by default. Pass `includeInactive=true` for the HR-admin catalog UI. " +
                    "Requires `employee.read`.")
    @GetMapping
    @RequirePermission("employee.read")
    public List<DisabilityDto> list(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return service.list(includeInactive);
    }

    @Operation(summary = "Create a disability category", description = "Requires `employee.admin`.")
    @ApiResponse(responseCode = "409", description = "DISABILITY_CODE_TAKEN.")
    @PostMapping
    @RequirePermission("employee.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public DisabilityDto create(@Valid @RequestBody CreateDisabilityRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Patch a disability", description = "Null fields stay unchanged. `code` is immutable. " +
            "Requires `employee.admin`.")
    @PatchMapping("/{id}")
    @RequirePermission("employee.admin")
    public DisabilityDto update(@PathVariable long id, @Valid @RequestBody UpdateDisabilityRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Deactivate a disability",
            description = "Soft-delete — flips `active=false`. Existing employee_disabilities rows are " +
                    "preserved. Re-enable via PATCH. Requires `employee.admin`.")
    @DeleteMapping("/{id}")
    @RequirePermission("employee.admin")
    public DisabilityDto deactivate(@PathVariable long id) {
        return service.deactivate(id);
    }
}
