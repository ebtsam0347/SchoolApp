package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelRequest;
import com.forward.hrms.employee.dto.request.travel.request.TransportVerifyRequest;
import com.forward.hrms.employee.dto.request.travel.request.TravelApproveRequest;
import com.forward.hrms.employee.dto.request.travel.request.TravelReasonRequest;
import com.forward.hrms.employee.dto.response.TravelRequestResponse;
import com.forward.hrms.employee.enums.travel.TravelRequestStatus;
import com.forward.hrms.employee.service.travel.TravelRequestService;

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

/**
 * Trip lifecycle:
 * {@code DRAFT → TRANSPORT_PENDING → DD_PENDING → APPROVED →
 * IN_PROGRESS → COMPLETED}, then {@code SETTLEMENT_PENDING → SETTLED} driven
 * by the settlement endpoints. Any stage rejection
 * ({@code TRANSPORT_REJECTED} / {@code DD_REJECTED}) is terminal. There is no
 * HR stage in this chain.
 */
@Tag(name = "Travel Requests",
        description = "Raise and track official travel. Approval hierarchy: TRANSPORT " +
                "(`travel.transport.verify`) → DD/Requester's Head (the requester's manager), " +
                "run on the TRAVEL_REQUEST chain; only the final DD/Head approval yields APPROVED and any " +
                "stage rejection is terminal — there is no HR stage. Reads require `travel.read`; " +
                "raising/editing requires `travel.write`; the Transport stage acts via " +
                "`POST /{id}/transport/verify` (`travel.transport.verify`); the DD/Head stage uses " +
                "`/{id}/approve` + `/{id}/reject` with `travel.write` (stage authority is enforced by the " +
                "approval chain).")
@RestController
@RequiredArgsConstructor
@RequestMapping("/travel/requests")
public class TravelRequestController {

    private final TravelRequestService service;

    @Operation(summary = "Create a travel request (starts in DRAFT)",
            description = "The caller is the requester; `participants` are co-travellers. Requires `travel.write`.")
    @ApiResponse(responseCode = "201", description = "Travel request created.")
    @ApiResponse(responseCode = "422", description = "End datetime before start, or no applicable policy.")
    @PostMapping
    @RequirePermission("travel.write")
    @ResponseStatus(HttpStatus.CREATED)
    public TravelRequestResponse create(@Valid @RequestBody CreateTravelRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Get a travel request", description = "Requires `travel.read`.")
    @ApiResponse(responseCode = "404", description = "Travel request not found.")
    @GetMapping("/{id}")
    @RequirePermission("travel.read")
    public TravelRequestResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Edit a DRAFT travel request",
            description = "Pre-submission correction by the creator: full-replace of header fields, "
                    + "participants and legs. Requires `travel.write`; only the requester may edit and "
                    + "only while the request is DRAFT.")
    @ApiResponse(responseCode = "403", description = "Caller is not the creator.")
    @ApiResponse(responseCode = "404", description = "Travel request not found.")
    @ApiResponse(responseCode = "409", description = "Travel request is not in DRAFT status.")
    @ApiResponse(responseCode = "422", description = "End datetime before start, or no applicable policy.")
    @PatchMapping("/{id}")
    @RequirePermission("travel.write")
    public TravelRequestResponse update(@PathVariable Long id, @Valid @RequestBody CreateTravelRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Search travel requests (paginated)",
            description = "All filters optional and combinable. Requires `travel.read`.")
    @GetMapping
    @RequirePermission("travel.read")
    public PageResponse<TravelRequestResponse> search(
            @Parameter(description = "Filter by status.") @RequestParam(required = false) TravelRequestStatus status,
            @Parameter(description = "Filter by travel type code.") @RequestParam(required = false) String travelTypeCode,
            @Parameter(description = "Filter by requester employee id.")
            @RequestParam(required = false) Long requesterEmployeeId,
            @Parameter(description = "Filter by policy version id.") @RequestParam(required = false) Long policyVersionId,
            @Parameter(hidden = true) Pageable pageable) {
        return PageResponse.from(service.search(status, travelTypeCode, requesterEmployeeId, policyVersionId,
                PageableDefaults.apply(pageable, "createdAt")));
    }

    @Operation(summary = "List the caller's own travel requests (paginated)", description = "Requires `travel.read`.")
    @GetMapping("/mine")
    @RequirePermission("travel.read")
    public PageResponse<TravelRequestResponse> listMine(@Parameter(hidden = true) Pageable pageable) {
        return PageResponse.from(service.listMine(PageableDefaults.apply(pageable, "createdAt")));
    }

    @Operation(summary = "Travel requests awaiting my approval — stage-aware inbox",
            description = "Paginated. Only rows the caller can act on at the current stage (the "
                    + "`travel.transport.verify` pool for TRANSPORT, the requester's manager for DD/Head, or "
                    + "a delegate). Date filter windows on the trip dates. `q` matches the request id (exact, "
                    + "when numeric) or a case-insensitive substring of the request number, requester employee "
                    + "code, or requester name; `travelTypeCode` is exact. Filters apply before pagination. "
                    + "Requires `travel.write`; stage authority is enforced by the approval chain, not this gate.")
    @GetMapping("/pending-approvals")
    @RequirePermission("travel.write")
    public PageResponse<TravelRequestResponse> pendingApprovals(
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @Parameter(description = "Request id, request number, requester employee code, or requester name.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Exact travel type code, e.g. FACTORY_VISIT.")
            @RequestParam(required = false) String travelTypeCode,
            @Parameter(hidden = true) Pageable pageable) {
        return service.pendingApprovals(from, to, q, travelTypeCode, PageableDefaults.apply(pageable, "createdAt"));
    }

    @Operation(summary = "Submit a DRAFT for approval (DRAFT → TRANSPORT_PENDING)",
            description = "Requires `travel.write`. Needs at least one participant and one journey leg, and a "
                    + "resolvable manager/HOD for the DD/Head stage. Opens the TRANSPORT → DD/Head chain "
                    + "at the Transport stage.")
    @ApiResponse(responseCode = "409", description = "Travel request is not in DRAFT status.")
    @ApiResponse(responseCode = "422", description = "No participants, no legs, or no resolvable manager/HOD.")
    @PostMapping("/{id}/submit")
    @RequirePermission("travel.write")
    public TravelRequestResponse submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @Operation(summary = "Transport verification — first approval stage (TRANSPORT_PENDING → DD_PENDING / TRANSPORT_REJECTED)",
            description = "Requires `travel.transport.verify`. On APPROVE, applies the supplied per-leg "
                    + "verifications (actual/validated distance and confirmed company-vehicle fields; the "
                    + "requester's planned distance is never overwritten), regenerates distance-based "
                    + "entitlements on the effective distance, and advances the request to DD/Head. On "
                    + "REJECT, the request becomes TRANSPORT_REJECTED and the chain terminates — no DD/Head "
                    + "stage is created.")
    @ApiResponse(responseCode = "403", description = "Caller cannot act on the Transport stage.")
    @ApiResponse(responseCode = "404", description = "Travel request or a referenced leg not found.")
    @ApiResponse(responseCode = "409", description = "Travel request is not in TRANSPORT_PENDING status.")
    @ApiResponse(responseCode = "422", description = "A supplied actual distance is negative.")
    @PostMapping("/{id}/transport/verify")
    @RequirePermission("travel.transport.verify")
    public TravelRequestResponse transportVerify(@PathVariable Long id,
                                                 @Valid @RequestBody TransportVerifyRequest req) {
        return service.transportVerify(id, req);
    }

    @Operation(summary = "Approve the current pending stage (DD/Requester's Head — final)",
            description = "Requires `travel.write`; the caller must additionally be able to act on the current "
                    + "stage — the requester's manager for DD/Head — enforced by the approval chain, not this "
                    + "permission. DD/Head approval is the final stage and moves the request straight to "
                    + "APPROVED. (Transport uses `/transport/verify`.)")
    @ApiResponse(responseCode = "403", description = "Caller cannot act on the current stage.")
    @ApiResponse(responseCode = "409", description = "Travel request is not at an actionable approval stage.")
    @PostMapping("/{id}/approve")
    @RequirePermission("travel.write")
    public TravelRequestResponse approve(@PathVariable Long id,
                                         @Valid @RequestBody(required = false) TravelApproveRequest req) {
        return service.approve(id, req == null ? null : req.note());
    }

    @Operation(summary = "Reject the current pending stage (→ TRANSPORT_REJECTED / DD_REJECTED)",
            description = "Requires `travel.write`; the caller must additionally be able to act on the "
                    + "current stage — enforced by the approval chain, not this permission. A rejection is "
                    + "terminal: no later stage is created. (Transport rejects via `/transport/verify`.)")
    @ApiResponse(responseCode = "403", description = "Caller cannot act on the current stage.")
    @ApiResponse(responseCode = "409", description = "Travel request is not at an actionable approval stage.")
    @PostMapping("/{id}/reject")
    @RequirePermission("travel.write")
    public TravelRequestResponse reject(@PathVariable Long id,
                                        @Valid @RequestBody(required = false) TravelReasonRequest req) {
        return service.reject(id, req == null ? null : req.reason());
    }

    @Operation(summary = "Cancel a travel request (DRAFT or SUBMITTED → CANCELLED)", description = "Requires `travel.write`.")
    @ApiResponse(responseCode = "409", description = "Travel request is not cancellable in its current status.")
    @PostMapping("/{id}/cancel")
    @RequirePermission("travel.write")
    public TravelRequestResponse cancel(@PathVariable Long id,
                                        @Valid @RequestBody(required = false) TravelReasonRequest req) {
        return service.cancel(id, req == null ? null : req.reason());
    }

    @Operation(summary = "Mark travel as started (APPROVED → IN_PROGRESS)", description = "Requires `travel.write`.")
    @ApiResponse(responseCode = "409", description = "Travel request is not in APPROVED status.")
    @PostMapping("/{id}/start")
    @RequirePermission("travel.write")
    public TravelRequestResponse start(@PathVariable Long id) {
        return service.startTravel(id);
    }

    @Operation(summary = "Mark travel as completed (IN_PROGRESS → COMPLETED)", description = "Requires `travel.write`.")
    @ApiResponse(responseCode = "409", description = "Travel request is not in IN_PROGRESS status.")
    @PostMapping("/{id}/complete")
    @RequirePermission("travel.write")
    public TravelRequestResponse complete(@PathVariable Long id) {
        return service.completeTravel(id);
    }
}
