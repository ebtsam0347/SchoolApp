package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.saga.SagaHealthSnapshot;
import com.forward.hrms.commons.saga.SagaState;
import com.forward.hrms.commons.saga.SagaService;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.response.SagaResponse;
import com.forward.hrms.employee.enums.EmployeeErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import com.forward.hrms.commons.web.page.PageableDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sagas",
        description = "Visibility into durable saga rows (onboarding ESS provisioning, "
                + "termination disable, etc.). Operators use these to inspect the status of "
                + "an in-flight saga and to re-arm a FAILED one once the underlying issue is "
                + "resolved. Requires `employee.read` / `employee.write`.")
@RestController
@RequestMapping("/sagas")
@RequiredArgsConstructor
public class SagaController {

    private final SagaService sagaService;

    @Operation(summary = "Get a saga by id", description = "Requires `employee.read`.")
    @ApiResponse(responseCode = "200", description = "Saga snapshot.")
    @ApiResponse(responseCode = "404", description = "Saga not found.")
    @GetMapping("/{id}")
    @RequirePermission("employee.read")
    public SagaResponse get(@Parameter(description = "Saga row id") @PathVariable long id) {
        return sagaService.findById(id)
                .map(SagaResponse::of)
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "saga not found"));
    }

    @Operation(summary = "Look up a saga by (name, correlationKey)",
            description = "Returns the unique saga for the pair (the substrate enforces "
                    + "`(saga_name, correlation_key)` uniqueness). Useful for FE to find "
                    + "the saga linked to an entity, e.g. "
                    + "`/sagas/by-key?name=ONBOARDING&correlationKey=employee:1042`. Requires `employee.read`.")
    @ApiResponse(responseCode = "404", description = "No saga matches.")
    @GetMapping("/by-key")
    @RequirePermission("employee.read")
    public SagaResponse find(
            @Parameter(description = "Saga name, e.g. ONBOARDING.") @RequestParam String name,
            @Parameter(description = "Correlation key, e.g. employee:1042.") @RequestParam String correlationKey) {
        return sagaService.findByKey(name, correlationKey)
                .map(SagaResponse::of)
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "saga not found"));
    }

    @Operation(summary = "List sagas (paginated)",
            description = "Filtered listing for operator views and the admin dashboard's failed-"
                    + "sagas slice. Both filters are optional. Default sort is `updatedAt,desc` "
                    + "so the freshest failures surface first. Requires `employee.read`.")
    @GetMapping
    @RequirePermission("employee.read")
    public PageResponse<SagaResponse> list(
            @Parameter(description = "Filter by status (RUNNING / COMPLETED / FAILED).")
            @RequestParam(required = false) SagaState.Status status,
            @Parameter(description = "Filter by saga name (e.g. ONBOARDING).")
            @RequestParam(required = false) String name,
            @Parameter(hidden = true) Pageable pageable) {
        Pageable p = PageableDefaults.apply(pageable, "updatedAt");
        return PageResponse.from(sagaService.search(status, name, p).map(SagaResponse::of));
    }

    @Operation(summary = "Saga health rollup",
            description = "Aggregate RUNNING + last-24h FAILED counts plus a per-name 24-hour "
                    + "failure-rate series. Drives the admin dashboard's saga summary card "
                    + "without forcing the BFF to scan the saga list. Requires `employee.read`.")
    @ApiResponse(responseCode = "200", description = "Health snapshot.")
    @GetMapping("/health")
    @RequirePermission("employee.read")
    public SagaHealthSnapshot health() {
        return sagaService.health();
    }

    @Operation(summary = "Re-arm a FAILED saga",
            description = "Resets a {@code FAILED} saga to {@code RUNNING} at its current "
                    + "step with {@code attempt_count=0}. No-op for sagas in any other state. "
                    + "Use after fixing the underlying upstream issue. Requires `employee.write`.")
    @ApiResponse(responseCode = "200", description = "Saga re-armed (or unchanged if not FAILED).")
    @ApiResponse(responseCode = "404", description = "Saga not found.")
    @PostMapping("/{id}/retry")
    @RequirePermission("employee.write")
    public SagaResponse retry(@Parameter(description = "Saga row id") @PathVariable long id) {
        return SagaResponse.of(sagaService.retry(id));
    }
}
