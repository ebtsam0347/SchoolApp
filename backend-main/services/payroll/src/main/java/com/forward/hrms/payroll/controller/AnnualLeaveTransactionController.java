package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.*;
import com.forward.hrms.payroll.dto.response.AnnualLeaveCalculateResponse;
import com.forward.hrms.payroll.dto.response.AnnualLeaveLockMonthResponse;
import com.forward.hrms.payroll.dto.response.AnnualLeaveResponse;
import com.forward.hrms.payroll.service.AnnualLeaveTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Annual Leave Controller", description = "Calculate Annual Leave for employees. Requires `payroll.read` / `payroll.admin`.")

@RestController
@RequestMapping("/annual-leave")
@RequiredArgsConstructor
public class AnnualLeaveTransactionController {

    private final AnnualLeaveTransactionService service;

    @Operation(summary = "Calculate annual leave",
            description = "Calculates annual leave for all eligible employees for the given month and year."
    )
    @PostMapping
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnualLeaveCalculateResponse calculate(
            @Valid @RequestBody AnnualLeaveCalculateRequest request) {

        return service.calculateAnnualLeave(request);
    }

    @GetMapping
    @RequirePermission("payroll.admin")
    public PageResponse<AnnualLeaveResponse> getAnnualLeaves(
            @RequestParam Integer year,
            @RequestParam Integer month,
            @RequestParam(required = false) String employeeCode,
            @RequestParam(required = false) Boolean paymentStatus,
            @RequestParam(required = false) Boolean paymentPending,
            @RequestParam(required = false) Boolean lockStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size

    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "employeeCode"));
        return service.getAnnualLeaves(new AnnualLeaveSearchRequest(year, month, employeeCode, paymentStatus, paymentPending, lockStatus), pageable);
    }

    @Operation(summary = "Update annual leave payment status",
            description = "If set to paid, only the payment status is updated. If set to unpaid, payable leave days are " +
                    "carried to the next annual leave cycle.")
    @PostMapping("/transaction/{id}/payment-status")
    @RequirePermission("payroll.admin")
    public AnnualLeaveResponse updatePaymentStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateAnnualLeavePaymentStatusRequest request) {
        return service.updatePaymentStatus(id, request);
    }

    @Operation(summary = "Lock annual leave month",
            description = "Permanently locks every annual leave transaction of the given month and year. " +
                    "Rejected with 409 while any transaction has no payment status (neither paid nor unpaid); " +
                    "the error's pendingCount says how many. There is no unlock.")
    @PostMapping("/lock")
    @RequirePermission("payroll.admin")
    public AnnualLeaveLockMonthResponse lockMonth(@Valid @RequestBody AnnualLeaveLockMonthRequest request) {
        return service.lockMonth(request);
    }

}
