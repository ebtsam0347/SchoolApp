package com.forward.hrms.attendance.dto.response;

import java.math.BigDecimal;

/**
 * Per-leave-type availed summary for a single payroll period, scoped to
 * every paid leave type except {@code ANNUAL} (which gets its own richer
 * {@link AnnualLeaveSummaryDto} block). Used by
 * {@code LeaveController#payslipSummary} for the payslip's leave section
 * (legacy "Casual Leaves Availed (By Month)" / "Total Casual Leaves
 * Availed" pair).
 */
public record LeaveTypeAvailedDto(

        String code,

        String name,

        BigDecimal availedThisMonth,

        BigDecimal availedThisCycle

) {}
