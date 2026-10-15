package com.forward.hrms.attendance.dto.response;

import java.math.BigDecimal;

/**
 * Medical (M1) + Casual leave balances summed into one bucket for
 * final-settlement purposes. Both types share the same FISCAL cycle
 * anchor and neither is individually encashable — only their combined
 * excess (used beyond earned) matters to the settlement.
 */
public record CombinedLeaveSummaryDto(

        BigDecimal allocated,

        BigDecimal earned,

        BigDecimal used,

        BigDecimal pending,

        BigDecimal payableDays,

        BigDecimal recoverableDays

) {}
