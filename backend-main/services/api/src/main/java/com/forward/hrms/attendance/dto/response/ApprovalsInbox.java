package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.employee.dto.response.EmployeeSummary;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ApprovalsInbox",
        description = "Combined approvals inbox. The four downstream pending-approvals lists fan " +
                "out in parallel, then each row is enriched with an `employee` summary in one " +
                "additional batch resolve. Counts are pre-computed for the badge UI.")
public record ApprovalsInbox(
        List<LeaveRow> leaves,
        List<OvertimeRow> overtime,
        List<RegularizationRow> regularization,
        List<ShortLeaveRow> shortLeave,
        Counts counts
) {
    @Schema(name = "ApprovalsInbox.LeaveRow")
    public record LeaveRow(LeaveRequestDto request,
                           @Schema(nullable = true) EmployeeSummary employee) {}

    @Schema(name = "ApprovalsInbox.OvertimeRow")
    public record OvertimeRow(OvertimeRequestDto request,
                              @Schema(nullable = true) EmployeeSummary employee,
                              @Schema(nullable = true, description = "The work date's punch-derived " +
                                      "minutes (zeros when there's no attendance record). Set on the " +
                                      "approvals inbox and the team / department / filed-by-me lists.")
                              OvertimeMinutes minutes) {
        public OvertimeRow(OvertimeRequestDto request, EmployeeSummary employee) {
            this(request, employee, null);
        }
    }

    @Schema(name = "ApprovalsInbox.OvertimeMinutes",
            description = "The day's attendance record minutes (as on OvertimeMineRow) — the OT " +
                    "buckets stay zero until the request is approved — plus `projectedOtMinutes`: " +
                    "the OT the day's punches earn whatever the request's status, i.e. what " +
                    "approving it would credit.")
    public record OvertimeMinutes(int otNormalMinutes, int otExtraMinutes,
                                  int otSpecialMinutes, int workedMinutes,
                                  int projectedOtMinutes) {
        public static final OvertimeMinutes NONE = new OvertimeMinutes(0, 0, 0, 0, 0);
    }

    @Schema(name = "ApprovalsInbox.RegularizationRow")
    public record RegularizationRow(RegularizationRequestDto request,
                                    @Schema(nullable = true) EmployeeSummary employee) {}

    @Schema(name = "ApprovalsInbox.ShortLeaveRow")
    public record ShortLeaveRow(ShortLeaveRequestDto request,
                                @Schema(nullable = true) EmployeeSummary employee) {}

    @Schema(name = "ApprovalsInbox.Counts",
            description = "Per-bucket counts plus the total — drives the badge in the nav.")
    public record Counts(int leaves, int overtime, int regularization, int shortLeave, int total) {}
}
