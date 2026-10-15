package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.saga.SagaHealthSnapshot;
import com.forward.hrms.commons.saga.SagaState;
import com.forward.hrms.commons.saga.SagaService;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.response.SagaResponse;

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
        description = "Visibility into durable saga rows for payroll runs (GENERATE_PDFS, " +
                "FINALIZE_RUN). HR uses these to inspect an in-flight post step and re-arm a " +
                "FAILED saga once the underlying issue is resolved. Requires `payroll.read` / `payroll.admin`.")
@RestController
@RequestMapping("/payroll/sagas")
@RequiredArgsConstructor
public class SagaController {

    private final SagaService sagaService;

    @Operation(summary = "Get a saga by id", description = "Requires `payroll.read`.")
    @ApiResponse(responseCode = "200", description = "Saga snapshot.")
    @ApiResponse(responseCode = "404", description = "Saga not found.")
    @GetMapping("/{id}")
    @RequirePermission("payroll.read")
    public SagaResponse get(@Parameter(description = "Saga row id") @PathVariable long id) {
        return sagaService.findById(id)
                .map(SagaResponse::of)
                .orElseThrow(() -> new NotFoundError(CommonErrorCode.NOT_FOUND, "saga not found"));
    }

    @Operation(summary = "Look up a saga by (name, correlationKey)",
            description = "Useful for FE to find the saga linked to a payroll run, " +
                    "e.g. `/sagas/by-key?name=PAYROLL_RUN_POST&correlationKey=payroll_run:42`. Requires `payroll.read`.")
    @ApiResponse(responseCode = "404", description = "No saga matches.")
    @GetMapping("/by-key")
    @RequirePermission("payroll.read")
    public SagaResponse find(
            @Parameter(description = "Saga name, e.g. PAYROLL_RUN_POST.") @RequestParam String name,
            @Parameter(description = "Correlation key, e.g. payroll_run:42.") @RequestParam String correlationKey) {
        return sagaService.findByKey(name, correlationKey)
                .map(SagaResponse::of)
                .orElseThrow(() -> new NotFoundError(CommonErrorCode.NOT_FOUND, "saga not found"));
    }

    @Operation(summary = "List sagas (paginated)",
            description = "Filtered listing for operator views and the admin dashboard's failed-"
                    + "sagas slice. Both filters are optional. Default sort is `updatedAt,desc`. "
                    + "Requires `payroll.read`.")
    @GetMapping
    @RequirePermission("payroll.read")
    public PageResponse<SagaResponse> list(
            @Parameter(description = "Filter by status (RUNNING / COMPLETED / FAILED).")
            @RequestParam(required = false) SagaState.Status status,
            @Parameter(description = "Filter by saga name.")
            @RequestParam(required = false) String name,
            @Parameter(hidden = true) Pageable pageable) {
        Pageable p = PageableDefaults.apply(pageable, "updatedAt");
        return PageResponse.from(sagaService.search(status, name, p).map(SagaResponse::of));
    }

    @Operation(summary = "Saga health rollup",
            description = "Aggregate RUNNING + last-24h FAILED counts plus a per-name 24-hour "
                    + "failure-rate series for payroll-owned sagas (PAYROLL_RUN_POST, …). "
                    + "Mirrors the employee-service shape so the BFF can merge both sources. "
                    + "Requires `payroll.read`.")
    @ApiResponse(responseCode = "200", description = "Health snapshot.")
    @GetMapping("/health")
    @RequirePermission("payroll.read")
    public SagaHealthSnapshot health() {
        return sagaService.health();
    }

    @Operation(summary = "Re-arm a FAILED saga",
            description = "Resets a FAILED saga to RUNNING at its current step with " +
                    "attempt_count=0. No-op for sagas in any other state. Use after fixing " +
                    "the underlying upstream issue (MinIO outage, etc.). Requires `payroll.admin`.")
    @ApiResponse(responseCode = "200", description = "Saga re-armed (or unchanged if not FAILED).")
    @PostMapping("/{id}/retry")
    @RequirePermission("payroll.admin")
    public SagaResponse retry(@PathVariable long id) {
        return SagaResponse.of(sagaService.retry(id));
    }
}
