package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "RunSkipAdvanceDto",
        description = "A skipped employee's running permanent advance — the one compute deducts from " +
                "(latest APPROVED). `advanceId` is null when nothing is outstanding.")
public record RunSkipAdvanceDto(
        long employeeId,
        @Schema(nullable = true, description = "Latest APPROVED permanent advance; null when none is running.")
        Long advanceId,
        @Schema(nullable = true) String requestCode,
        @Schema(description = "Sum of every APPROVED permanent advance, rupees.") BigDecimal taken,
        @Schema(description = "Sum of every deduction already in the ledger, rupees.") BigDecimal deducted,
        @Schema(description = "`taken − deducted`, rupees.") BigDecimal outstanding,
        @Schema(nullable = true, description = "The advance's monthly installment, rupees. Not deducted " +
                "this month when `waivedThisMonth`.")
        BigDecimal monthlyDeduction,
        @Schema(description = "HR waived the installment for the run's month: compute deducts 0 and writes a 0 " +
                "ledger row; the advance's installment is unchanged.")
        boolean waivedThisMonth
) {}
