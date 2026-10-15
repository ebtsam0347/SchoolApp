package com.forward.hrms.employee.controller;

import com.forward.hrms.attendance.dto.response.ApprovalDecisionRow;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.employee.enums.RequestType;
import com.forward.hrms.employee.service.ApprovalHistoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Read-only sibling of {@code /requisitions/pending-approvals},
 * {@code /factory-tours/pending-approvals} and the travel
 * {@code /travel/**\/pending-approvals} inboxes — unified across both
 * requisition types, both tour types and all three travel types
 * (TRAVEL_REQUEST / TRAVEL_ADVANCE / TRAVEL_SETTLEMENT), scoped to the
 * caller as <em>decider</em>. Mirrors attendance's
 * {@code ApprovalHistoryController}.
 */
@Tag(name = "Approval history",
        description = "Lists approval decisions (APPROVED or REJECTED) the caller closed at any " +
                "stage of any requisition / factory-tour / travel request, in a date range. " +
                "Requires `employee.read`.")
@RestController
@RequestMapping("/approvals")
@RequiredArgsConstructor
public class ApprovalHistoryController {

    private final ApprovalHistoryService history;

    @Operation(summary = "My approval-decision history",
            description = "Returns one row per stage the caller closed (APPROVED or REJECTED) — " +
                    "unified across REQUISITION_NEW_HIRE / REQUISITION_REPLACEMENT / " +
                    "FACTORY_TOUR_LOCAL / FACTORY_TOUR_INTL / TRAVEL_REQUEST / TRAVEL_ADVANCE / " +
                    "TRAVEL_SETTLEMENT. Always scoped to the caller; there is " +
                    "no `deciderId` query param. `from` and `to` are required, `to` is inclusive, " +
                    "and the window must be at most 366 days. Default sort `decidedAt,desc`. " +
                    "Default page size 20 (max 200).")
    @ApiResponse(responseCode = "422", description = "APPROVAL_HISTORY_DATE_RANGE_INVALID — missing " +
            "dates, inverted range, or window over 366 days.")
    @GetMapping("/history")
    public PageResponse<ApprovalDecisionRow> myHistory(
            @Parameter(required = true, description = "Decision date lower bound (inclusive).")
            @RequestParam LocalDate from,
            @Parameter(required = true, description = "Decision date upper bound (inclusive).")
            @RequestParam LocalDate to,
            @Parameter(description = "Filter by request type (REQUISITION_NEW_HIRE / " +
                    "REQUISITION_REPLACEMENT / TRAVEL_REQUEST / " +
                    "TRAVEL_ADVANCE / TRAVEL_SETTLEMENT). Omit for all.")
            @RequestParam(required = false) RequestType type,
            @Parameter(description = "Filter by decision outcome. Omit for both.",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                            allowableValues = {"APPROVED", "REJECTED"}))
            @RequestParam(required = false) StepStatus decision,
            @Parameter(hidden = true) Pageable pageable) {
        return history.history(from, to, type, decision,
                PageableDefaults.apply(pageable, "decidedAt"));
    }
}
