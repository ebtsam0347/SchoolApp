package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.ExitStatus;
import com.forward.hrms.employee.enums.ExitType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Schema(name = "ExitDto", description = "Current or historical exit row for an employee.")
public record ExitDto(
        long id,
        long employeeId,
        ExitType exitType,
        @Schema(nullable = true) LocalDate noticeStartDate,
        LocalDate lastWorkingDay,
        @Schema(nullable = true) String reason,
        @Schema(nullable = true) String notes,
        ExitStatus status,
        @Schema(nullable = true) LocalDateTime completedAt,
        @Schema(nullable = true) LocalDateTime withdrawnAt,
        @Schema(nullable = true) String withdrawnReason
) {}
