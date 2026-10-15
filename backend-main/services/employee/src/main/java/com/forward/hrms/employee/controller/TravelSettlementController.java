package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelSettlementRequest;
import com.forward.hrms.employee.dto.request.travel.request.SettleTravelSettlementRequest;
import com.forward.hrms.employee.dto.request.travel.request.TravelApproveRequest;
import com.forward.hrms.employee.dto.request.travel.request.TravelReasonRequest;
import com.forward.hrms.employee.dto.request.travel.response.TravelSettlementResponse;
import com.forward.hrms.employee.service.travel.TravelSettlementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

import java.util.List;

/**
 * Reconciles a participant's approved expenses against advances paid, plus
 * adjustments, into a final payable/receivable balance.
 * {@code DRAFT → SUBMITTED → HR_APPROVED → PAID} (or REJECTED /
 * CANCELLED). Approval runs the 2-stage TRAVEL_SETTLEMENT chain — HR
 * Settlement Verification, then Accounts Payment Confirmation;
 * {@code settle} closes the Accounts stage, drives the linked advances to
 * SETTLED and the trip {@code SETTLEMENT_PENDING → SETTLED}.
 */
@Tag(name = "Travel Settlements",
        description = "Final reconciliation of a participant's travel expenses and advances. " +
                "final = Σ approved expenses − Σ advances paid ± adjustments. Flow: DRAFT → SUBMITTED → " +
                "HR_APPROVED → PAID (or REJECTED / CANCELLED). Reads require `travel.read`; raising/editing " +
                "requires `travel.write`; HR approve/reject at SUBMITTED require `travel.write` (stage " +
                "authority — `travel.settlement.approve` — is enforced by the approval chain); the Accounts " +
                "payment-confirmation action at HR_APPROVED requires `travel.settlement.paid`.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/travel")
public class TravelSettlementController {

    private final TravelSettlementService service;

    @Operation(summary = "Create a settlement for a participant",
            description = "The trip must be COMPLETED or SETTLEMENT_PENDING. Items are validated against the " +
                    "source expenses/advances. Moves the trip to SETTLEMENT_PENDING. Requires `travel.write`.")
    @ApiResponse(responseCode = "201", description = "Settlement created.")
    @ApiResponse(responseCode = "404", description = "Travel request, participant or a source record not found.")
    @ApiResponse(responseCode = "409", description = "Trip not COMPLETED, or a FINAL settlement already exists.")
    @ApiResponse(responseCode = "422", description = "A source record does not belong to the participant, or is not settleable.")
    @PostMapping("/requests/{travelRequestId}/settlements")
    @RequirePermission("travel.write")
    @ResponseStatus(HttpStatus.CREATED)
    public TravelSettlementResponse create(@PathVariable Long travelRequestId,
                                           @Valid @RequestBody CreateTravelSettlementRequest req) {
        return service.create(travelRequestId, req);
    }

    @Operation(summary = "Prepare the FINAL settlement for one participant (server-assembled, DRAFT)",
            description = "The trip must be COMPLETED or SETTLEMENT_PENDING. The server discovers this " +
                    "participant's own settleable expenses (SUBMITTED / APPROVED / PARTIALLY_APPROVED / PAID — " +
                    "un-reviewed SUBMITTED expenses are included) and paid advances, builds the settlement " +
                    "items, runs the existing settlement calculation and " +
                    "returns a DRAFT FINAL settlement — the caller supplies no expense/advance ids. " +
                    "Idempotent: an existing DRAFT FINAL settlement for the participant is returned unchanged; " +
                    "any other active FINAL settlement is a 409. Requires `travel.write`.")
    @ApiResponse(responseCode = "200", description = "DRAFT settlement prepared, or the existing draft returned.")
    @ApiResponse(responseCode = "404", description = "Travel request or participant not found.")
    @ApiResponse(responseCode = "409", description = "Trip not COMPLETED, or a FINAL settlement already exists.")
    @ApiResponse(responseCode = "422",
            description = "Participant does not belong to the request / is not travelling, or no settleable records exist.")
    @PostMapping("/requests/{travelRequestId}/participants/{participantId}/settlement/prepare")
    @RequirePermission("travel.write")
    public TravelSettlementResponse prepareFinalSettlement(@PathVariable Long travelRequestId,
                                                           @PathVariable Long participantId) {
        return service.prepareFinalSettlement(travelRequestId, participantId);
    }

    @Operation(summary = "List the settlements of a travel request", description = "Requires `travel.read`.")
    @GetMapping("/requests/{travelRequestId}/settlements")
    @RequirePermission("travel.read")
    public List<TravelSettlementResponse> listForRequest(@PathVariable Long travelRequestId) {
        return service.listForRequest(travelRequestId);
    }

    @Operation(summary = "List the settlements of a participant", description = "Requires `travel.read`.")
    @GetMapping("/participants/{participantId}/settlements")
    @RequirePermission("travel.read")
    public List<TravelSettlementResponse> listForParticipant(@PathVariable Long participantId) {
        return service.listForParticipant(participantId);
    }

    @Operation(summary = "Settlements awaiting my approval — stage-aware inbox",
            description = "Paginated. Only rows the caller can act on at the current stage (the " +
                    "`travel.settlement.approve` HR pool for SUBMITTED, the `travel.settlement.paid` " +
                    "Accounts pool for HR_APPROVED, or a delegate). Date filter windows on the settlement " +
                    "creation date. Requires `travel.write`; stage authority is enforced by the approval " +
                    "chain, not this gate.")
    @GetMapping("/settlements/pending-approvals")
    @RequirePermission("travel.write")
    public PageResponse<TravelSettlementResponse> pendingApprovals(
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @Parameter(hidden = true) Pageable pageable) {
        return service.pendingApprovals(from, to, pageable);
    }

    @Operation(summary = "Get a settlement (with its items)", description = "Requires `travel.read`.")
    @ApiResponse(responseCode = "404", description = "Settlement not found.")
    @GetMapping("/settlements/{id}")
    @RequirePermission("travel.read")
    public TravelSettlementResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Recalculate totals and direction from the persisted items (DRAFT only)",
            description = "Requires `travel.write`.")
    @ApiResponse(responseCode = "409", description = "Settlement is not in DRAFT status.")
    @PostMapping("/settlements/{id}/recalculate")
    @RequirePermission("travel.write")
    public TravelSettlementResponse recalculate(@PathVariable Long id) {
        return service.recalculate(id);
    }

    @Operation(summary = "Submit a DRAFT settlement for approval (DRAFT → SUBMITTED)", description = "Requires `travel.write`.")
    @ApiResponse(responseCode = "409", description = "Settlement is not in DRAFT status.")
    @ApiResponse(responseCode = "422", description = "Settlement has no items.")
    @PostMapping("/settlements/{id}/submit")
    @RequirePermission("travel.write")
    public TravelSettlementResponse submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @Operation(summary = "HR Settlement Verification approval (SUBMITTED → HR_APPROVED)",
            description = "Requires `travel.write`; the caller must additionally hold "
                    + "`travel.settlement.approve` (the HR stage pool) — enforced by the approval "
                    + "chain, not this permission. Only acts on SUBMITTED; use "
                    + "`/settlements/{id}/settle` for the Accounts payment-confirmation stage.")
    @ApiResponse(responseCode = "403", description = "Caller cannot act on the current stage.")
    @ApiResponse(responseCode = "409", description = "Settlement is not in SUBMITTED status.")
    @PostMapping("/settlements/{id}/approve")
    @RequirePermission("travel.write")
    public TravelSettlementResponse approve(@PathVariable Long id,
                                            @Valid @RequestBody(required = false) TravelApproveRequest req) {
        return service.approve(id, req == null ? null : req.note());
    }

    @Operation(summary = "HR Settlement Verification rejection (SUBMITTED → REJECTED, terminal)",
            description = "Requires `travel.write`; the caller must additionally hold "
                    + "`travel.settlement.approve` — enforced by the approval chain, not this permission. "
                    + "Only acts on SUBMITTED.")
    @ApiResponse(responseCode = "403", description = "Caller cannot act on the current stage.")
    @ApiResponse(responseCode = "409", description = "Settlement is not in SUBMITTED status.")
    @PostMapping("/settlements/{id}/reject")
    @RequirePermission("travel.write")
    public TravelSettlementResponse reject(@PathVariable Long id,
                                           @Valid @RequestBody(required = false) TravelReasonRequest req) {
        return service.reject(id, req == null ? null : req.reason());
    }

    @Operation(summary = "Cancel a settlement (DRAFT or SUBMITTED → CANCELLED)",
            description = "Requires `travel.write`. Reopens the trip to COMPLETED when no active settlements remain.")
    @ApiResponse(responseCode = "409", description = "Settlement is not cancellable in its current status.")
    @PostMapping("/settlements/{id}/cancel")
    @RequirePermission("travel.write")
    public TravelSettlementResponse cancel(@PathVariable Long id,
                                           @Valid @RequestBody(required = false) TravelReasonRequest req) {
        return service.cancel(id, req == null ? null : req.reason());
    }

    @Operation(summary = "Accounts Payment Confirmation (HR_APPROVED → PAID)",
            description = "Requires `travel.settlement.paid`. Only acts on HR_APPROVED — an HR approval must " +
                    "happen first. Closes the Accounts stage of the approval chain, records the payment " +
                    "reference, drives each linked advance to SETTLED, and moves the trip to SETTLED once " +
                    "every travelling participant is PAID.")
    @ApiResponse(responseCode = "403", description = "Caller does not hold `travel.settlement.paid`.")
    @ApiResponse(responseCode = "409", description = "Settlement is not in HR_APPROVED status.")
    @PostMapping("/settlements/{id}/settle")
    @RequirePermission("travel.settlement.paid")
    public TravelSettlementResponse settle(@PathVariable Long id,
                                           @Valid @RequestBody(required = false) SettleTravelSettlementRequest req) {
        return service.settle(id,
                req == null ? null : req.settlementPaymentReference(),
                req == null ? null : req.externalTransactionId());
    }
}
