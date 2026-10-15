package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.SuspensionStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "SuspensionDto", description = "One row in an employee's suspension history.")
public record SuspensionDto(
        long id,
        long employeeId,
        LocalDate startDate,
        @Schema(description = "Planned end. Null on open-ended suspensions.", nullable = true) LocalDate endDate,
        String reason,
        @Schema(description = "When false, suspended days count as LOP for payroll.") boolean withPay,
        SuspensionStatus status,
        @Schema(nullable = true) LocalDate liftedOn,
        @Schema(nullable = true) String liftedReason
) {}
