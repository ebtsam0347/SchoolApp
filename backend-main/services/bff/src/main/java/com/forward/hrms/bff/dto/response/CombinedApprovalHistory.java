package com.forward.hrms.bff.dto.response;

import com.forward.hrms.attendance.dto.response.ApprovalDecisionRow;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Cross-service {@code GET /bff/approvals/history} composite: the caller's
 * approval-decision history across attendance (leave/OT/regularization/
 * short-leave), payroll (permanent/short-term advances), and employee
 * (requisitions/factory-tours) — each fanned out to its owning service's
 * own {@code /approvals/history} in parallel, merged into one list sorted
 * newest-decided-first. Mirrors {@link CombinedApprovalsInbox}'s shape:
 * no cross-service DB pagination (each source is a single generous fetch),
 * a source that fails soft-fails to an empty bucket rather than failing
 * the call.
 */
@Schema(name = "CombinedApprovalHistory",
        description = "The caller's approval-decision history across attendance, payroll, and " +
                "employee in one call, for a required date range.")
public record CombinedApprovalHistory(
        @Schema(description = "Merged rows, sorted by decidedAt descending.")
        List<ApprovalDecisionRow> rows,
        @Schema(description = "rows.size() — no further pagination is attempted cross-service.")
        int total,
        @Schema(description = "Names of sources that failed to load (empty when everything succeeded). " +
                "Possible values: attendance, payroll, employee.")
        List<String> erroredSources
) {}
