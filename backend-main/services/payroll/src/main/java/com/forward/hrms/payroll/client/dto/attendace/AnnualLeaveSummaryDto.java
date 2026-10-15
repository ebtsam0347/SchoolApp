package com.forward.hrms.payroll.client.dto.attendace;

import java.math.BigDecimal;

/** Mirrors attendance-service's {@code AnnualLeaveSummaryDto}. */
public record AnnualLeaveSummaryDto(
        BigDecimal total,
        BigDecimal availed,
        BigDecimal paid,
        BigDecimal balance
) {}
