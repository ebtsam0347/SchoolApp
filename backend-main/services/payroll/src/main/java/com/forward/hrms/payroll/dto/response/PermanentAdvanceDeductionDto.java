package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.PermanentAdvanceDeduction;
import com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Schema(name = "PermanentAdvanceDeductionDto")
public record PermanentAdvanceDeductionDto(
        long id,
        long employeeId,
        BigDecimal deductedAmount,
        @Schema(description = "Where it was recovered from. Only MANUAL rows can be deleted directly.")
        PermanentAdvanceDeductionSource source,
        @Schema(nullable = true, description = "Payroll run / bonus transaction / annual leave transaction / " +
                "final settlement id, by source. Null for MANUAL.")
        Long sourceRefId,
        BigDecimal remainingBalance,
        LocalDate transactionDate,
        @Schema(nullable = true) String description,
        LocalDateTime createdAt
) {
    public static PermanentAdvanceDeductionDto of(PermanentAdvanceDeduction d) {
        return new PermanentAdvanceDeductionDto(
                d.getId(), d.getEmployeeId(), d.getDeductedAmount(),
                d.getSource(), d.getSourceRefId(),
                d.getRemainingBalance(), d.getTransactionDate(),
                d.getDescription(), d.getCreatedAt());
    }
}
