package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "RegularizationRequestDto")
public record RegularizationRequestDto(
        long id,
        @Schema(description = "Human-readable code, e.g. REG-2026-0008.") String requestCode,
        long employeeId,
        LocalDate workDate,
        @Schema(nullable = true) LocalDateTime requestedCheckIn,
        @Schema(nullable = true) LocalDateTime requestedCheckOut,
        String reason,
        String status,
        @Schema(nullable = true) CurrentStageDto currentStage,
        List<ApprovalStepDto> steps,
        List<String> actions,
        LocalDateTime createdAt
) {}
