package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelAdvanceRequest;
import com.forward.hrms.employee.dto.request.travel.request.MarkTravelAdvancePaidRequest;
import com.forward.hrms.employee.dto.request.travel.request.ReconcileTravelAdvanceRequest;
import com.forward.hrms.employee.dto.request.travel.request.TravelApproveRequest;
import com.forward.hrms.employee.dto.request.travel.request.TravelReasonRequest;
import com.forward.hrms.employee.dto.request.travel.response.TravelAdvanceResponse;
import com.forward.hrms.employee.service.travel.TravelAdvanceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
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

/**
 * Advances against a Travel Request.
 * {@code DRAFT → SUBMITTED → APPROVED → PAID → SETTLED} (or REJECTED /
 * CANCELED). Approval runs the TRAVEL_ADVANCE chain (manager → HR →
 * accounts); {@code SETTLED} is driven by the settlement endpoints.
 */
@Tag(name = "Travel Advances",
        description = "Request, approve, pay, reconcile and settle travel advances. Flow: DRAFT → SUBMITTED → " +
                "APPROVED → PAID → SETTLED. Approval chain: manager → HR (`travel.approve`) → accounts " +
                "(`travel.advance.approve`). Reads require `travel.read`; raising/editing/approving requires " +
                "`travel.write` (stage authority is enforced by the approval chain); the payment / " +
                "reconciliation actions require `travel.advance.approve`.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/travel")
public class TravelAdvanceController {

    private final TravelAdvanceService service;

    @Operation(summary = "Raise an advance against a travel request",
            description = "The trip must be APPROVED or IN_PROGRESS. Requires `travel.write`.")
    @ApiResponse(responseCode = "201", description = "Advance created.")
    @ApiResponse(responseCode = "404", description = "Travel request or participant not found.")
    @ApiResponse(responseCode = "409", description = "Trip is not APPROVED / IN_PROGRESS.")
    @PostMapping("/requests/{travelRequestId}/advances")
    @RequirePermission("travel.write")
    @ResponseStatus(HttpStatus.CREATED)
    public TravelAdvanceResponse create(@PathVariable Long travelRequestId,
                                        @Valid @RequestBody CreateTravelAdvanceRequest req) {
        return service.create(travelRequestId, req);
    }

    @Operation(summary = "List the advances of a travel request", description = "Requires `travel.read`.")
    @GetMapping("/requests/{travelRequestId}/advances")
    @RequirePermission("travel.read")
    public List<TravelAdvanceResponse> listForRequest(@PathVariable Long travelRequestId) {
        return service.listForRequest(travelRequestId);
    }

    @Operation(summary = "List the advances of a participant", description = "Requires `travel.read`.")
    @GetMapping("/participants/{participantId}/advances")
    @RequirePermission("travel.read")
    public List<TravelAdvanceResponse> listForParticipant(@PathVariable Long participantId) {
        return service.listForParticipant(participantId);
    }

    @Operation(summary = "Advances awaiting my approval — stage-aware inbox",
            description = "Paginated. Only rows the caller can act on at the current stage (the submitter's "
                    + "manager for stage 1, the `travel.approve` HR pool for stage 2, the "
                    + "`travel.advance.approve` accounts pool for stage 3, or a delegate). Date filter windows "
                    + "on the advance creation date. Requires `travel.write` — stage authority is enforced by "
                    + "the approval chain, not this gate.")
    @GetMapping("/advances/pending-approvals")
    @RequirePermission("travel.write")
    public PageResponse<TravelAdvanceResponse> pendingApprovals(
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @Parameter(hidden = true) Pageable pageable) {
        return service.pendingApprovals(from, to, pageable);
    }

    @Operation(summary = "Get an advance", description = "Requires `travel.read`.")
    @ApiResponse(responseCode = "404", description = "Advance not found.")
    @GetMapping("/advances/{id}")
    @RequirePermission("travel.read")
    public TravelAdvanceResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Update a DRAFT advance", description = "Requires `travel.write`.")
    @ApiResponse(responseCode = "409", description = "Advance is not in DRAFT status.")
    @PatchMapping("/advances/{id}")
    @RequirePermission("travel.write")
    public TravelAdvanceResponse update(@PathVariable Long id,
                                        @Valid @RequestBody CreateTravelAdvanceRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Submit a DRAFT advance for approval (DRAFT → SUBMITTED)", description = "Requires `travel.write`.")
    @ApiResponse(responseCode = "409", description = "Advance is not in DRAFT status.")
    @PostMapping("/advances/{id}/submit")
    @RequirePermission("travel.write")
    public TravelAdvanceResponse submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @Operation(summary = "Approve the current pending stage",
            description = "Requires `travel.write`; the caller must additionally be able to act on the "
                    + "current stage (stage 1 is the participant's MANAGER, stage 2 is the `travel.approve` "
                    + "HR pool, stage 3 is the `travel.advance.approve` accounts pool) — enforced by the "
                    + "approval chain, not this permission.")
    @ApiResponse(responseCode = "403", description = "Caller cannot act on the current stage.")
    @ApiResponse(responseCode = "409", description = "Advance is not in SUBMITTED / UNDER_REVIEW status.")
    @PostMapping("/advances/{id}/approve")
    @RequirePermission("travel.write")
    public TravelAdvanceResponse approve(@PathVariable Long id,
                                         @Valid @RequestBody(required = false) TravelApproveRequest req) {
        return service.approve(id, req == null ? null : req.note());
    }

    @Operation(summary = "Reject the current pending stage (→ REJECTED)",
            description = "Requires `travel.write`; the caller must additionally be able to act on the "
                    + "current stage — enforced by the approval chain, not this permission.")
    @ApiResponse(responseCode = "403", description = "Caller cannot act on the current stage.")
    @ApiResponse(responseCode = "409", description = "Advance is not in SUBMITTED / UNDER_REVIEW status.")
    @PostMapping("/advances/{id}/reject")
    @RequirePermission("travel.write")
    public TravelAdvanceResponse reject(@PathVariable Long id,
                                        @Valid @RequestBody(required = false) TravelReasonRequest req) {
        return service.reject(id, req == null ? null : req.reason());
    }

    @Operation(summary = "Cancel an advance (DRAFT or SUBMITTED → CANCELED)", description = "Requires `travel.write`.")
    @ApiResponse(responseCode = "409", description = "Advance is not cancellable in its current status.")
    @PostMapping("/advances/{id}/cancel")
    @RequirePermission("travel.write")
    public TravelAdvanceResponse cancel(@PathVariable Long id,
                                        @Valid @RequestBody(required = false) TravelReasonRequest req) {
        return service.cancel(id, req == null ? null : req.reason());
    }

    @Operation(summary = "Queue an approved advance for payment (APPROVED → PAYMENT_PENDING)",
            description = "Requires `travel.advance.approve`.")
    @ApiResponse(responseCode = "409", description = "Advance is not in an approved status.")
    @PostMapping("/advances/{id}/mark-payment-pending")
    @RequirePermission("travel.advance.approve")
    public TravelAdvanceResponse markPaymentPending(@PathVariable Long id) {
        return service.markPaymentPending(id);
    }

    @Operation(summary = "Record the payment Finance released (→ PAID)",
            description = "Requires `travel.advance.approve`. `paidAmount` defaults to and may not exceed the " +
                    "approved amount.")
    @ApiResponse(responseCode = "409", description = "Advance is not in an approved / payment-pending status.")
    @ApiResponse(responseCode = "422", description = "Paid amount exceeds the approved amount.")
    @PostMapping("/advances/{id}/mark-paid")
    @RequirePermission("travel.advance.approve")
    public TravelAdvanceResponse markPaid(@PathVariable Long id,
                                          @Valid @RequestBody(required = false) MarkTravelAdvancePaidRequest req) {
        return service.markPaid(id,
                req == null ? null : req.paidAmount(),
                req == null ? null : req.paidCurrencyCode(),
                req == null ? null : req.paymentReference(),
                req == null ? null : req.externalPaymentId(),
                req == null ? null : req.paymentMethod());
    }

    @Operation(summary = "Tick the finance-reconciliation flag on a paid advance",
            description = "Requires `travel.advance.approve`. Does not change status.")
    @ApiResponse(responseCode = "409", description = "Advance is not in PAID / SETTLED status.")
    @ApiResponse(responseCode = "422", description = "Advance has no recorded payment.")
    @PostMapping("/advances/{id}/reconcile")
    @RequirePermission("travel.advance.approve")
    public TravelAdvanceResponse reconcile(@PathVariable Long id,
                                           @Valid @RequestBody(required = false) ReconcileTravelAdvanceRequest req) {
        return service.reconcilePayment(id, req == null ? null : req.externalPaymentId());
    }
}
