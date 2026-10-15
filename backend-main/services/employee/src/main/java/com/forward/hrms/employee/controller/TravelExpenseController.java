package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelExpenseItemRequest;
import com.forward.hrms.employee.dto.request.travel.request.MarkTravelExpensePaidRequest;
import com.forward.hrms.employee.dto.request.travel.request.TravelExpenseReviewRequest;
import com.forward.hrms.employee.dto.request.travel.request.TravelReasonRequest;
import com.forward.hrms.employee.dto.request.travel.response.TravelExpenseItemResponse;
import com.forward.hrms.employee.service.travel.TravelExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Actual expense claims against a Travel Request. Single-reviewer flow —
 * {@code SUBMITTED → APPROVED / PARTIALLY_APPROVED → PAID} (or REJECTED /
 * CANCELLED). The multi-stage approval is on the aggregate settlement.
 */
@Tag(name = "Travel Expenses",
        description = "Claim and review actual travel expenses. Flow: SUBMITTED → APPROVED / " +
                "PARTIALLY_APPROVED → PAID (or REJECTED / CANCELLED). Reads require `travel.read`; " +
                "claiming/editing requires `travel.write`; review requires `travel.approve`; " +
                "marking paid requires `travel.settlement.approve`.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/travel")
public class TravelExpenseController {

    private final TravelExpenseService service;

    @Operation(summary = "Add an expense claim to a travel request",
            description = "The trip must be APPROVED or later. Requires `travel.write`.")
    @ApiResponse(responseCode = "201", description = "Expense created.")
    @ApiResponse(responseCode = "404", description = "Travel request, participant or entitlement not found.")
    @ApiResponse(responseCode = "409", description = "Trip is not in a claimable status.")
    @PostMapping("/requests/{travelRequestId}/expenses")
    @RequirePermission("travel.write")
    @ResponseStatus(HttpStatus.CREATED)
    public TravelExpenseItemResponse create(@PathVariable Long travelRequestId,
                                            @Valid @RequestBody CreateTravelExpenseItemRequest req) {
        return service.create(travelRequestId, req);
    }

    @Operation(summary = "List the expenses of a travel request", description = "Requires `travel.read`.")
    @GetMapping("/requests/{travelRequestId}/expenses")
    @RequirePermission("travel.read")
    public List<TravelExpenseItemResponse> listForRequest(@PathVariable Long travelRequestId) {
        return service.listForRequest(travelRequestId);
    }

    @Operation(summary = "List the expenses of a participant", description = "Requires `travel.read`.")
    @GetMapping("/participants/{participantId}/expenses")
    @RequirePermission("travel.read")
    public List<TravelExpenseItemResponse> listForParticipant(@PathVariable Long participantId) {
        return service.listForParticipant(participantId);
    }

    @Operation(summary = "Get an expense", description = "Requires `travel.read`.")
    @ApiResponse(responseCode = "404", description = "Expense not found.")
    @GetMapping("/expenses/{id}")
    @RequirePermission("travel.read")
    public TravelExpenseItemResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Update a SUBMITTED expense", description = "Requires `travel.write`.")
    @ApiResponse(responseCode = "409", description = "Expense is not in SUBMITTED status.")
    @PatchMapping("/expenses/{id}")
    @RequirePermission("travel.write")
    public TravelExpenseItemResponse update(@PathVariable Long id,
                                            @Valid @RequestBody CreateTravelExpenseItemRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Approve an expense (→ APPROVED / PARTIALLY_APPROVED)",
            description = "Requires `travel.approve`. `approvedAmount` defaults to the eligible amount; " +
                    "it must be between 0 and the claimed amount.")
    @ApiResponse(responseCode = "409", description = "Expense is not in a reviewable status.")
    @ApiResponse(responseCode = "422", description = "Approved amount out of range.")
    @PostMapping("/expenses/{id}/approve")
    @RequirePermission("travel.approve")
    public TravelExpenseItemResponse approve(@PathVariable Long id,
                                             @Valid @RequestBody(required = false) TravelExpenseReviewRequest req) {
        return service.approve(id,
                req == null ? null : req.approvedAmount(),
                req == null ? null : req.note());
    }

    @Operation(summary = "Reject an expense (→ REJECTED)", description = "Requires `travel.approve`.")
    @ApiResponse(responseCode = "409", description = "Expense is not in a reviewable status.")
    @PostMapping("/expenses/{id}/reject")
    @RequirePermission("travel.approve")
    public TravelExpenseItemResponse reject(@PathVariable Long id,
                                            @Valid @RequestBody(required = false) TravelReasonRequest req) {
        return service.reject(id, req == null ? null : req.reason());
    }

    @Operation(summary = "Cancel an expense (→ CANCELLED)", description = "Requires `travel.write`.")
    @ApiResponse(responseCode = "409", description = "Expense is not in a cancellable status.")
    @PostMapping("/expenses/{id}/cancel")
    @RequirePermission("travel.write")
    public TravelExpenseItemResponse cancel(@PathVariable Long id,
                                            @Valid @RequestBody(required = false) TravelReasonRequest req) {
        return service.cancel(id, req == null ? null : req.reason());
    }

    @Operation(summary = "Mark an approved expense as paid (→ PAID)", description = "Requires `travel.settlement.approve`.")
    @ApiResponse(responseCode = "409", description = "Expense is not in an approved status.")
    @PostMapping("/expenses/{id}/mark-paid")
    @RequirePermission("travel.settlement.approve")
    public TravelExpenseItemResponse markPaid(@PathVariable Long id,
                                              @Valid @RequestBody(required = false) MarkTravelExpensePaidRequest req) {
        return service.markPaid(id, req == null ? null : req.paymentReference());
    }
}
