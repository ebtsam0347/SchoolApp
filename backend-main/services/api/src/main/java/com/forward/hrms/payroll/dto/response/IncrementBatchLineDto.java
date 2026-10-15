package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.IncrementBatchLineStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "IncrementBatchLineDto")
public record IncrementBatchLineDto(
        long id,
        long batchId,
        long employeeId,
        BigDecimal oldBasic,
        BigDecimal newBasic,
        @Schema(nullable = true) BigDecimal oldIncomeTax,
        @Schema(nullable = true) BigDecimal newIncomeTax,
        @Schema(nullable = true) LocalDate effectiveFrom,
        IncrementBatchLineStatus status,
        @Schema(nullable = true) String errorMessage
) {}
