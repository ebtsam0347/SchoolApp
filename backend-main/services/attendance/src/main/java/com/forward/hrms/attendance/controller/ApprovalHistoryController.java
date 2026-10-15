package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.dto.response.ApprovalDecisionRow;
import com.forward.hrms.attendance.enums.RequestType;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.attendance.service.ApprovalHistoryService;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;

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
 * §12.2 — "approvals I have decided" history. Read-only sibling of the
 * pending-inbox endpoints scattered across {@code /leaves/pending-approvals},
 * {@code /overtime/pending-approvals}, etc. — except this one is unified
 * across types and scoped to the caller as <em>decider</em>, not as
 * <em>assignee</em>.
 */
@Tag(name = "Approval history",
        description = "Lists approval decisions (APPROVED or REJECTED) the caller closed at any " +
                "stage of any leave / OT / regularization / short-leave request, in a date range. " +
                "Used by the manager / HR \"my approvals\" screen. Requires `attendance.read`.")
@RestController
@RequestMapping("/approvals")
@RequiredArgsConstructor
public class ApprovalHistoryController {

    private final ApprovalHistoryService history;

    @Operation(summary = "My approval-decision history",
            description = "Returns one row per stage the caller closed (APPROVED or REJECTED) — " +
                    "unified across leave / OT / regularization / short leave. The result is always " +
                    "scoped to the caller; there is no `deciderId` query param. `from` and `to` are " +
                    "required, `to` is inclusive, and the window must be at most 366 days. " +
                    "Default sort `decidedAt,desc`. Default page size 20 (max 200).")
    @ApiResponse(responseCode = "422", description = "REQUEST_DATE_OUT_OF_RANGE — missing dates, " +
            "inverted range, or window over 366 days.")
    @GetMapping("/history")
    public PageResponse<ApprovalDecisionRow> myHistory(
            @Parameter(required = true,
                    description = "Decision date lower bound (inclusive).")
            @RequestParam LocalDate from,
            @Parameter(required = true,
                    description = "Decision date upper bound (inclusive).")
            @RequestParam LocalDate to,
            @Parameter(description = "Filter by request type. Omit for all.")
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
