package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.PayslipStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "PayslipDto")
public record PayslipDto(
        long id,
        long payrollRunId,
        long employeeId,
        String employeeCode,
        @Schema(description = "Calendar year of the run period.", example = "2026")
        int periodYear,
        @Schema(description = "Calendar month of the run period (1-12).", example = "4")
        int periodMonth,
        @Schema(description = "Fiscal year code of the run period.", example = "FY26")
        String fiscalYearCode,
        BigDecimal basic,
        BigDecimal allowances,
        BigDecimal gross,
        BigDecimal deductions,
        BigDecimal tax,
        BigDecimal eobi,
        BigDecimal pessiEmployer,
        BigDecimal net,
        BigDecimal lopDays,
        @Schema(nullable = true) Integer workingDays,
        @Schema(nullable = true) BigDecimal paidDays,
        @Schema(nullable = true) BigDecimal absentDays,
        @Schema(nullable = true) Integer holidaysInPeriod,
        @Schema(nullable = true,
                description = "Count of WEEKEND-status days in the period. Renamed from sundaysInPeriod (§7.payroll #25); the system supports any day-of-week as the weekly off.")
        Integer weekendsInPeriod,
        BigDecimal otHours,
        BigDecimal otAmount,
        @Schema(nullable = true) String pdfObjectKey,
        @Schema(nullable = true) LocalDateTime generatedAt,
        PayslipStatus status,
        @Schema(nullable = true) String heldReason,
        @Schema(nullable = true) Long releasedInRunId,
        @Schema(description = "Optional — included only on detail reads.", nullable = true)
        List<PayslipLineDto> lines
) {}
