package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "AnnualLeaveUnpaidCarryResponse")
public record AnnualLeaveUnpaidCarryResponse(
        @Schema(description = "Created leave balance ID")
        Long leaveBalanceId,

        @Schema(description = "Employee ID")
        Long employeeId,

        @Schema(description = "Cycle start date of the created balance")
        LocalDate cycleStart,

        @Schema(description = "Cycle end date of the created balance")
        LocalDate cycleEnd,

        @Schema(description = "Number of carry-over days")
        String carryDays
) {
}
