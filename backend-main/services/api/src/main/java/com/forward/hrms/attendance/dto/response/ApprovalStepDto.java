package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "ApprovalStepDto",
        description = "One stage in the approval chain. `assignedToName` / `decidedByName` are " +
                "looked up from employee-service when the controller has access; null otherwise.")
public record ApprovalStepDto(
        int stageNo,
        String stageRole,
        @Schema(nullable = true) Long assignedTo,
        @Schema(nullable = true) String assignedToName,
        String status,
        @Schema(nullable = true) Long decidedBy,
        @Schema(nullable = true) String decidedByName,
        @Schema(nullable = true) LocalDateTime decidedAt,
        @Schema(nullable = true) String note
) {}
