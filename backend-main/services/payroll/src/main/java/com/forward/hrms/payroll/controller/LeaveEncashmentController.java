package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.CreateLeaveEncashmentRequest;
import com.forward.hrms.payroll.dto.request.MarkLeaveEncashmentPaidRequest;
import com.forward.hrms.payroll.dto.response.LeaveEncashmentDto;
import com.forward.hrms.payroll.enums.LeaveEncashmentStatus;
import com.forward.hrms.payroll.service.LeaveEncashmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Leave encashments",
        description = "Annual leave encashment payouts. Created in PENDING; HR marks PAID once disbursed " +
                "(via a payroll run or off-cycle). Requires `payroll.read` / `payroll.write`.")
@RestController
@RequestMapping("/leave-encashments")
@RequiredArgsConstructor
public class LeaveEncashmentController {

    private final LeaveEncashmentService service;

    @Operation(summary = "List encashments",
            description = "Default sort by id desc. Requires `payroll.read`.")
    @GetMapping
    @RequirePermission("payroll.read")
    public PageResponse<LeaveEncashmentDto> list(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) LeaveEncashmentStatus status,
            @Parameter(hidden = true) Pageable pageable) {
        return service.search(employeeId, status, PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Get one", description = "Requires `payroll.read`.")
    @GetMapping("/{id}")
    @RequirePermission("payroll.read")
    public LeaveEncashmentDto get(@PathVariable long id) {
        return service.get(id);
    }

    @Operation(summary = "Create — opens in PENDING. Amount is derived.",
            description = "Requires `payroll.write`.")
    @PostMapping
    @RequirePermission("payroll.write")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveEncashmentDto create(@Valid @RequestBody CreateLeaveEncashmentRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Mark paid — PENDING → PAID",
            description = "Optionally tag the payroll run that disbursed it. Requires `payroll.write`.")
    @PostMapping("/{id}/mark-paid")
    @RequirePermission("payroll.write")
    public LeaveEncashmentDto markPaid(@PathVariable long id,
                                       @RequestBody(required = false) MarkLeaveEncashmentPaidRequest body) {
        return service.markPaid(id, body);
    }

    @Operation(summary = "Cancel — pre-payment only", description = "Requires `payroll.write`.")
    @PostMapping("/{id}/cancel")
    @RequirePermission("payroll.write")
    public LeaveEncashmentDto cancel(@PathVariable long id) {
        return service.cancel(id);
    }
}
