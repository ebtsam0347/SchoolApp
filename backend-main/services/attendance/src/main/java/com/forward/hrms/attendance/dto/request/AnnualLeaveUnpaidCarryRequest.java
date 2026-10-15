package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "AnnualLeaveUnpaidCarryRequest")
public record AnnualLeaveUnpaidCarryRequest(
        @NotNull
        @Schema(description = "Payroll annual leave transaction ID")
        Long annualLeaveTransactionId,

        @NotNull
        @Schema(description = "Employee ID")
        Long employeeId,

        @NotNull
        @Positive
        @Schema(description = "Leave type ID (typically 1 for ANNUAL)")
        Long leaveTypeId,

        @NotNull
        @Positive
        @Schema(description = "Number of leave days to carry over")
        BigDecimal carryDays,

        @NotNull
        @Schema(description = "Employee's joining date (used to calculate next cycle start)")
        LocalDate joiningDate
) {
}
