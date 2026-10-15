package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.CreateIncrementBatchLineRequest;
import com.forward.hrms.payroll.dto.request.CreateIncrementBatchRequest;
import com.forward.hrms.payroll.dto.request.UpdateIncrementBatchLineRequest;
import com.forward.hrms.payroll.dto.request.UpdateIncrementBatchRequest;
import com.forward.hrms.payroll.dto.response.IncrementBatchDto;
import com.forward.hrms.payroll.dto.response.IncrementBatchLineDto;
import com.forward.hrms.payroll.enums.IncrementBatchStatus;
import com.forward.hrms.payroll.service.IncrementBatchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@Tag(name = "Increment batches",
        description = "Bulk basic-salary revisions: annual cycle, mid-year adjustments, " +
                "one-off promotions. Requires `payroll.read` / `payroll.admin`.")
@RestController
@RequestMapping("/salary/increment-batches")
@RequiredArgsConstructor
public class IncrementBatchController {

    private final IncrementBatchService service;

    @Operation(summary = "List increment batches", description = "Requires `payroll.read`.")
    @GetMapping
    @RequirePermission("payroll.read")
    public List<IncrementBatchDto> list(@Parameter @RequestParam(required = false) IncrementBatchStatus status,
                                        @Parameter @RequestParam(required = false, name = "fiscalYear") String fy) {
        return service.list(status, fy);
    }

    @Operation(summary = "Get one (with per-line diffs and validation errors)",
            description = "Requires `payroll.read`.")
    @GetMapping("/{id}")
    @RequirePermission("payroll.read")
    public IncrementBatchDto get(@PathVariable long id) {
        return service.get(id);
    }

    @Operation(summary = "Create a DRAFT batch (JSON form — also covers ad-hoc 1-line batches)",
            description = "Requires `payroll.admin`.")
    @PostMapping
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public IncrementBatchDto create(@Valid @RequestBody CreateIncrementBatchRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Update a DRAFT batch", description = "Requires `payroll.admin`.")
    @PatchMapping("/{id}")
    @RequirePermission("payroll.admin")
    public IncrementBatchDto update(@PathVariable long id,
                                    @Valid @RequestBody UpdateIncrementBatchRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Add a line to a DRAFT batch",
            description = "409 if the batch already has a line for that employee. " +
                    "Requires `payroll.admin`.")
    @PostMapping("/{id}/lines")
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public IncrementBatchLineDto addLine(@PathVariable long id,
                                         @Valid @RequestBody CreateIncrementBatchLineRequest req) {
        return service.addLine(id, req);
    }

    @Operation(summary = "Update a DRAFT batch line", description = "Requires `payroll.admin`.")
    @PatchMapping("/{id}/lines/{lineId}")
    @RequirePermission("payroll.admin")
    public IncrementBatchLineDto updateLine(@PathVariable long id, @PathVariable long lineId,
                                            @Valid @RequestBody UpdateIncrementBatchLineRequest req) {
        return service.updateLine(id, lineId, req);
    }

    @Operation(summary = "Delete a DRAFT batch line", description = "Requires `payroll.admin`.")
    @DeleteMapping("/{id}/lines/{lineId}")
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLine(@PathVariable long id, @PathVariable long lineId) {
        service.deleteLine(id, lineId);
    }

    @Operation(summary = "Apply a DRAFT batch (DRAFT → APPLIED, single transaction)",
            description = "Refuses if any line is ERROR. Re-apply on an APPLIED batch is a no-op." +
                    " Requires `payroll.admin`.")
    @PostMapping("/{id}/apply")
    @RequirePermission("payroll.admin")
    public IncrementBatchDto apply(@PathVariable long id) {
        return service.apply(id);
    }

    @Operation(summary = "Cancel a DRAFT batch", description = "Requires `payroll.admin`.")
    @PostMapping("/{id}/cancel")
    @RequirePermission("payroll.admin")
    public IncrementBatchDto cancel(@PathVariable long id) {
        return service.cancel(id);
    }
}
