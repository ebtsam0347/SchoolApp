package com.forward.hrms.bff.dto.response;

import com.forward.hrms.attendance.dto.response.ApprovalsInbox;
import com.forward.hrms.employee.dto.response.FactoryToursInbox;
import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.employee.dto.response.RequisitionDto;
import com.forward.hrms.employee.dto.response.TravelRequestResponse;
import com.forward.hrms.payroll.dto.response.PermanentAdvanceDto;
import com.forward.hrms.payroll.dto.response.ShortTermAdvanceBatchDto;
import com.forward.hrms.payroll.enums.AdvanceApprovalStage;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Cross-service {@code GET /bff/approvals/inbox} composite: leave, overtime,
 * regularization, and short leave (attendance's own combined inbox, employee-enriched)
 * plus requisitions, factory tours, travel requests, permanent advances, and
 * short-term advances — each fanned out to its owning service in parallel.
 * Mirrors {@link ApprovalsInbox}'s bucketed shape (flat list per source, plus
 * per-source and total counts) rather than collapsing everything into one
 * undifferentiated list — the source shapes are too different to merge.
 *
 * <p>A source that errors or times out doesn't fail the whole response: its
 * bucket comes back empty and its name lands in {@code erroredSources} so
 * the FE can tell "empty" from "this one didn't load."
 */
@Schema(name = "CombinedApprovalsInbox",
        description = "Every pending approval across attendance, employee, and payroll in one call.")
public record CombinedApprovalsInbox(
        List<ApprovalsInbox.LeaveRow> leaves,
        List<ApprovalsInbox.OvertimeRow> overtime,
        List<ApprovalsInbox.RegularizationRow> regularization,
        List<ApprovalsInbox.ShortLeaveRow> shortLeave,
        List<RequisitionDto> requisitions,
//        List<FactoryToursInbox.Row> factoryTours,
        List<TravelRequestResponse> travelRequests,
        List<PermanentAdvanceRow> permanentAdvances,
        List<ShortTermAdvanceRow> shortTermAdvances,
        Counts counts,
        @Schema(description = "Names of sources that failed to load (empty when everything succeeded). " +
                "Possible values: attendance, requisitions, factory-tours, travel-requests, " +
                "permanent-advances, short-term-advances.")
        List<String> erroredSources
) {
    @Schema(name = "CombinedApprovalsInbox.PermanentAdvanceRow")
    public record PermanentAdvanceRow(
            PermanentAdvanceDto request,
            AdvanceApprovalStage approvalStage,
            @Schema(nullable = true) EmployeeSummary employee
    ) {}

    @Schema(name = "CombinedApprovalsInbox.ShortTermAdvanceRow",
            description = "No single employee to attach — a batch spans N employees (per-employee " +
                    "detail is in the batch's lines, omitted on the list read).")
    public record ShortTermAdvanceRow(
            ShortTermAdvanceBatchDto request,
            AdvanceApprovalStage approvalStage
    ) {}

    @Schema(name = "CombinedApprovalsInbox.Counts",
            description = "Per-source counts reflect the total pending, not just what's on this page " +
                    "(each fan-out fetches a large single page); \"total\" sums all nine.")
    public record Counts(
            int leaves, int overtime, int regularization, int shortLeave,
            int requisitions, //int factoryTours,
            int travelRequests,
            int permanentAdvances, int shortTermAdvances,
            int total
    ) {}
}
