package com.forward.hrms.payroll.dto.response;

import java.math.BigDecimal;

/**
 * Medical (M1) + Casual leave balances summed into one bucket, valued
 * in money by payroll (attendance only supplies the day counts).
 * Neither type is individually encashable — encashmentAmount is
 * always zero here; only recoveryAmount (excess used beyond earned)
 * applies.
 */
public record CombinedLeaveSummaryDto(

        BigDecimal allocated,

        BigDecimal earned,

        BigDecimal used,

        BigDecimal pending,

        BigDecimal payableDays,

        BigDecimal recoverableDays,

        BigDecimal encashmentAmount,

        BigDecimal recoveryAmount

) {
}
