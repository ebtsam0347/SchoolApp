package com.forward.hrms.attendance.dto.response;

/**
 * Final-settlement leave summary, scoped to exactly the leave types
 * that matter for termination: Annual (kept separate — the only
 * encashable type) and Medical(M1) + Casual (combined into one
 * bucket — neither is individually encashable). Any other leave
 * type (unpaid, maternity, etc.) has no bearing on the settlement
 * and is intentionally excluded.
 */
public record TerminationLeavesSummaryDto(

        TerminationLeaveSummaryDto annualLeave,

        CombinedLeaveSummaryDto medicalAndCasualLeave

) {}
