package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.CreateOffCyclePaymentRequest;
import com.forward.hrms.payroll.dto.request.PayOffCyclePaymentRequest;
import com.forward.hrms.payroll.dto.request.UpdateOffCyclePaymentRequest;
import com.forward.hrms.payroll.dto.response.OffCyclePaymentDto;
import com.forward.hrms.payroll.enums.OffCyclePaymentSource;
import com.forward.hrms.payroll.enums.OffCyclePaymentStatus;
import com.forward.hrms.payroll.service.OffCyclePaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
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

import java.time.LocalDate;

@Tag(name = "Off-cycle payments",
        description = "Payments outside the regular monthly cycle: cash extras, special OT, "
                + "ad-hoc bonuses, eid bonus installment apply rows. Two authorities — HR creates "
                + "(which authorizes it), edits and cancels while HR_APPROVED; Accounts pays. "
                + "Reads need `payroll.read`, HR actions `payroll.write`, payment "
                + "`payroll.offcycle.accounts`.")
@RestController
@RequestMapping("/off-cycle-payments")
@RequiredArgsConstructor
public class OffCyclePaymentController {

    private final OffCyclePaymentService service;

    @Operation(summary = "Filter off-cycle payments",
            description = "Filter by `status=HR_APPROVED` for the Accounts payment queue. "
                    + "Requires `payroll.read`.")
    @GetMapping
    @RequirePermission("payroll.read")
    public PageResponse<OffCyclePaymentDto> list(
            @Parameter @RequestParam(required = false) Long employeeId,
            @Parameter @RequestParam(required = false) LocalDate from,
            @Parameter @RequestParam(required = false) LocalDate to,
            @Parameter(description = "Transaction source. Not the HR-facing payment type — "
                    + "those live in employee-service's off-cycle-payment-types master.")
            @RequestParam(required = false) OffCyclePaymentSource source,
            @Parameter(description = "Lifecycle state. Omit for all states.")
            @RequestParam(required = false) OffCyclePaymentStatus status,
            @Parameter(hidden = true) Pageable pageable) {
        return service.filter(employeeId, from, to, source, status,
                PageableDefaults.apply(pageable, "paidOn"));
    }

    @Operation(summary = "Get one", description = "Requires `payroll.read`.")
    @ApiResponse(responseCode = "404", description = "No such payment")
    @GetMapping("/{id}")
    @RequirePermission("payroll.read")
    public OffCyclePaymentDto get(@PathVariable long id) {
        return service.get(id);
    }

    @Operation(summary = "Record an HR-driven off-cycle payment",
            description = "Creation is also the HR authorization — the row is born `HR_APPROVED` "
                    + "with `hrApprovedBy` / `hrApprovedAt` stamped, and `source` is always `HR`. "
                    + "`offCyclePaymentTypeId` must name an active row in employee-service's "
                    + "off-cycle-payment-types master. Requires `payroll.write`.")
    @ApiResponse(responseCode = "422", description = "Payment type missing, unknown or inactive")
    @PostMapping
    @RequirePermission("payroll.write")
    @ResponseStatus(HttpStatus.CREATED)
    public OffCyclePaymentDto create(@Valid @RequestBody CreateOffCyclePaymentRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Patch an HR-recorded payment",
            description = "Allowed while `HR_APPROVED`; the row stays `HR_APPROVED`. A `PAID` or "
                    + "`CANCELLED` payment is immutable. Requires `payroll.write`.")
    @ApiResponse(responseCode = "409", description = "Not `HR_APPROVED`, or a concurrent edit won")
    @ApiResponse(responseCode = "422", description = "Payment type unknown or inactive")
    @PatchMapping("/{id}")
    @RequirePermission("payroll.write")
    public OffCyclePaymentDto update(@PathVariable long id,
                                     @Valid @RequestBody UpdateOffCyclePaymentRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Cancel — HR_APPROVED → CANCELLED",
            description = "HR withdraws an authorized payment before Accounts pays it. The row is "
                    + "kept, not deleted. HR only: Accounts is the payment authority and does not "
                    + "cancel HR's authorization. Requires `payroll.write`.")
    @ApiResponse(responseCode = "409", description = "Already `PAID` or `CANCELLED`")
    @PostMapping("/{id}/cancel")
    @RequirePermission("payroll.write")
    public OffCyclePaymentDto cancel(@PathVariable long id) {
        return service.cancel(id);
    }

    @Operation(summary = "Mark paid — HR_APPROVED → PAID",
            description = "Accounts records the actual payment and releases it. Requires "
                    + "`payroll.offcycle.accounts`, which HR deliberately does not hold — "
                    + "`payroll.write` alone will not do.")
    @ApiResponse(responseCode = "409", description = "Not `HR_APPROVED` — already paid or cancelled")
    @PostMapping("/{id}/pay")
    @RequirePermission("payroll.offcycle.accounts")
    public OffCyclePaymentDto pay(@PathVariable long id,
                                  @Valid @RequestBody PayOffCyclePaymentRequest req) {
        return service.pay(id, req);
    }

    @Operation(summary = "Retired — use POST /{id}/cancel",
            description = "Off-cycle payments are no longer hard-deleted; the lifecycle keeps the "
                    + "financial record. Always answers 409 (or 404 for an unknown id). Kept mapped "
                    + "so an old caller gets a message naming the replacement.",
            deprecated = true)
    @ApiResponse(responseCode = "409", description = "Always — cancel instead")
    @DeleteMapping("/{id}")
    @RequirePermission("payroll.write")
    @Deprecated
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
