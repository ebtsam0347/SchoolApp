package com.forward.hrms.payroll.dto.response;

import java.math.BigDecimal;

/**
 * Final-settlement leave summary consumed from attendance-service,
 * scoped to exactly the leave types that matter for termination:
 * Annual (kept separate — the only encashable type) and Medical(M1) +
 * Casual (combined into one bucket). totalEncashment/totalRecovery
 * are payroll-computed convenience rollups (attendance only supplies
 * day counts, not money).
 */
public record TerminationLeavesSummaryDto(

        TerminationLeaveSummaryDto annualLeave,

        CombinedLeaveSummaryDto medicalAndCasualLeave,

        BigDecimal totalEncashment,

        BigDecimal totalRecovery

) {}
