package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(name = "OvertimeRequestDto")
public record OvertimeRequestDto(
        long id,
        @Schema(description = "Human-readable code, e.g. OT-2026-0017.") String requestCode,
        long employeeId,
        LocalDate workDate,
        @Schema(description = "NORMAL / EXTRA / SPECIAL — the bucket the day's overtime is credited to.")
        String overtimeType,
        @Schema(nullable = true,
                description = "Factory / unit the overtime is worked for — B34002…B34007 or " +
                        "Administrator. Null only on requests filed before the field existed.")
        String factoryCode,
        @Schema(nullable = true) String reason,
        @Schema(nullable = true) UUID bulkBatchId,
        String status,
        @Schema(nullable = true) CurrentStageDto currentStage,
        List<ApprovalStepDto> steps,
        List<String> actions,
        LocalDateTime createdAt
) {}
