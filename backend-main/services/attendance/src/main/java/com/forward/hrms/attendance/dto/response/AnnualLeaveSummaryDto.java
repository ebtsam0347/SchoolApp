package com.forward.hrms.attendance.dto.response;

import java.math.BigDecimal;

/**
 * Annual leave's dedicated payslip block (legacy "A.L Total / Availed /
 * Paid / Balance"). {@code availed} is always derived from approved leave
 * records in the current cycle. {@code total}, {@code paid} and
 * {@code balance} come from the cycle's balance row and are {@code null}
 * when no balance row exists (entitlement unknown — never guessed).
 */
public record AnnualLeaveSummaryDto(

        BigDecimal total,

        BigDecimal availed,

        BigDecimal paid,

        BigDecimal balance

) {}
