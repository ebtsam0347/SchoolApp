package com.forward.hrms.bff.controller;

import com.forward.hrms.bff.dto.response.CombinedApprovalHistory;
import com.forward.hrms.bff.dto.response.CombinedApprovalsInbox;
import com.forward.hrms.bff.service.ApprovalHistoryCompositeService;
import com.forward.hrms.bff.service.ApprovalsInboxCompositeService;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnprocessableError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Approvals — Composites",
        description = "One-shot combined pending-approvals inbox and approval-decision history across " +
                "attendance (leave/overtime/regularization/short-leave), employee (requisitions/" +
                "factory-tours/travel-requests), and payroll (permanent/short-term advances). Each source " +
                "is caller-scoped by its owning service exactly as its standalone endpoint is; a source " +
                "that fails soft-fails to an empty bucket rather than failing the call.")
@RestController
@RequestMapping("/bff/approvals")
@RequiredArgsConstructor
public class ApprovalsInboxController {

    private static final java.util.Set<String> OVERTIME_TYPES = java.util.Set.of("NORMAL", "EXTRA", "SPECIAL");
    private static final java.util.Set<String> REQUISITION_TYPES = java.util.Set.of("NEW_HIRE", "REPLACEMENT");

    private final ApprovalsInboxCompositeService composite;
    private final ApprovalHistoryCompositeService historyComposite;

    @Operation(summary = "Combined pending-approvals inbox",
            description = "Fans out to six sources in parallel and merges into one response, mirroring " +
                    "attendance's own /approvals/inbox shape (flat list per source, per-source + total " +
                    "counts). See erroredSources for any bucket that failed to load. `leaveQ` / " +
                    "`leaveTypeId` filter the leaves bucket, `shortLeaveQ` the short-leave bucket, " +
                    "`overtimeQ` / `overtimeType` the overtime bucket, `regularizationQ` the " +
                    "regularization bucket, `requisitionQ` / `requisitionType` / `requisitionDepartmentId` the requisitions bucket, " +
                    "`shortTermAdvanceQ` the short-term-advances bucket, `permanentAdvanceQ` the " +
                    "permanent-advances bucket, and `travelQ` / `travelTypeCode` / `travelFrom` / `travelTo` " +
                    "the travel-requests bucket; " +
                    "a filtered bucket's count reflects the filter.")
    @ApiResponse(responseCode = "200", description = "Composite payload.")
    @ApiResponse(responseCode = "401", description = "Missing identity headers.")
    @GetMapping("/inbox")
    @PreAuthorize("isAuthenticated()")
    public CombinedApprovalsInbox inbox(
            @Parameter(description = "Leaves: request id (exact), or substring of request code, " +
                    "employee code, or employee name.")
            @RequestParam(required = false) String leaveQ,
            @Parameter(description = "Leaves: exact leave type id.")
            @RequestParam(required = false) Long leaveTypeId,
            @Parameter(description = "Short leaves: request id (exact), or substring of request code, " +
                    "employee code, or employee name.")
            @RequestParam(required = false) String shortLeaveQ,
            @Parameter(description = "Overtime: request id (exact), or substring of request code, " +
                    "employee code, or employee name.")
            @RequestParam(required = false) String overtimeQ,
            @Parameter(description = "Overtime: exact overtime type.",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {
                            "NORMAL", "EXTRA", "SPECIAL"}))
            @RequestParam(required = false) String overtimeType,
            @Parameter(description = "Regularizations: request id (exact), or substring of request code, " +
                    "employee code, or employee name.")
            @RequestParam(required = false) String regularizationQ,
            @Parameter(description = "Requisitions: exact request id.")
            @RequestParam(required = false) String requisitionQ,
            @Parameter(description = "Requisitions: exact requisition type.",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {
                            "NEW_HIRE", "REPLACEMENT"}))
            @RequestParam(required = false) String requisitionType,
            @Parameter(description = "Requisitions: exact department id.")
            @RequestParam(required = false) Long requisitionDepartmentId,
            @Parameter(description = "Short-term advances: batch id (exact), or substring of request code.")
            @RequestParam(required = false) String shortTermAdvanceQ,
            @Parameter(description = "Permanent advances: advance id (exact), or substring of request code, " +
                    "employee code, or employee name.")
            @RequestParam(required = false) String permanentAdvanceQ,
            @Parameter(description = "Travel requests: request id (exact), or substring of request number, " +
                    "requester employee code, or requester name.")
            @RequestParam(required = false) String travelQ,
            @Parameter(description = "Travel requests: exact travel type code, e.g. FACTORY_VISIT.")
            @RequestParam(required = false) String travelTypeCode,
            @Parameter(description = "Travel requests: trips overlapping on/after this date (inclusive).")
            @RequestParam(required = false) java.time.LocalDate travelFrom,
            @Parameter(description = "Travel requests: trips overlapping on/before this date (inclusive).")
            @RequestParam(required = false) java.time.LocalDate travelTo) {
        String otType = blankToNull(overtimeType);
        // Validate here: attendance would 400 on an unknown value, and the
        // composite soft-fails that to four empty attendance buckets.
        if (otType != null && !OVERTIME_TYPES.contains(otType.toUpperCase(java.util.Locale.ROOT))) {
            throw new UnprocessableError(CommonErrorCode.UNPROCESSABLE,
                    "overtimeType must be one of " + OVERTIME_TYPES);
        }
        String reqType = blankToNull(requisitionType);
        // Same reason: employee would 400, soft-failing the requisitions bucket to empty.
        if (reqType != null && !REQUISITION_TYPES.contains(reqType.toUpperCase(java.util.Locale.ROOT))) {
            throw new UnprocessableError(CommonErrorCode.UNPROCESSABLE,
                    "requisitionType must be one of " + REQUISITION_TYPES);
        }
        if (travelFrom != null && travelTo != null && travelFrom.isAfter(travelTo)) {
            throw new UnprocessableError(CommonErrorCode.UNPROCESSABLE, "travelFrom must not be after travelTo");
        }
        return composite.build(blankToNull(leaveQ), leaveTypeId, blankToNull(shortLeaveQ),
                blankToNull(overtimeQ), otType == null ? null : otType.toUpperCase(java.util.Locale.ROOT),
                blankToNull(regularizationQ), blankToNull(requisitionQ),
                reqType == null ? null : reqType.toUpperCase(java.util.Locale.ROOT), requisitionDepartmentId,
                blankToNull(shortTermAdvanceQ), blankToNull(permanentAdvanceQ),
                blankToNull(travelQ), blankToNull(travelTypeCode), travelFrom, travelTo);
    }

    @Operation(summary = "Combined approval-decision history",
            description = "Fans out to attendance's, payroll's, and employee's own /approvals/history " +
                    "in parallel and merges into one list sorted newest-decided-first. `from` and `to` " +
                    "are required and mirror each source's own 366-day window cap. See erroredSources " +
                    "for any source that failed to load.")
    @ApiResponse(responseCode = "200", description = "Composite payload.")
    @ApiResponse(responseCode = "401", description = "Missing identity headers.")
    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public CombinedApprovalHistory history(
            @Parameter(required = true, description = "Decision date lower bound (inclusive).")
            @RequestParam LocalDate from,
            @Parameter(required = true, description = "Decision date upper bound (inclusive).")
            @RequestParam LocalDate to,
            @Parameter(description = "Filter by request type. Omit for all.",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {
                            "LEAVE", "SHORT_LEAVE", "OVERTIME", "REGULARIZATION",
                            "PERMANENT_ADVANCE", "SHORT_TERM_ADVANCE",
                            "REQUISITION_NEW_HIRE", "REQUISITION_REPLACEMENT",
                            "TRAVEL_REQUEST", "TRAVEL_ADVANCE", "TRAVEL_SETTLEMENT"}))
            @RequestParam(required = false) String type,
            @Parameter(description = "Filter by decision outcome. Omit for both.",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                            allowableValues = {"APPROVED", "REJECTED"}))
            @RequestParam(required = false) String decision) {
        return historyComposite.build(from, to, blankToNull(type), blankToNull(decision));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
