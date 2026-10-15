package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(name = "ShortLeaveRequestDto")
public record ShortLeaveRequestDto(
        long id,
        @Schema(description = "Human-readable code, e.g. SL-2026-0017.") String requestCode,
        long employeeId,
        LocalDate leaveDate,
        LocalTime startTime,
        LocalTime endTime,
        @Schema(description = "Derived from (startTime, endTime).") int totalMinutes,
        @Schema(nullable = true) String reason,
        String status,
        @Schema(nullable = true) CurrentStageDto currentStage,
        List<ApprovalStepDto> steps,
        List<String> actions,
        LocalDateTime createdAt
) {}
