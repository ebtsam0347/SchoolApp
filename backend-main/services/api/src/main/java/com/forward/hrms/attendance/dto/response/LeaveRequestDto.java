package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "LeaveRequestDto", description = "Leave request DTO returned to submitters and approvers.")
public record LeaveRequestDto(
        long id,
        @Schema(description = "Human-readable code, e.g. LR-2026-0042.") String requestCode,
        long employeeId,
        long leaveTypeId,
        @Schema(description = "Leave type code, denormalized.") String leaveTypeCode,
        LocalDate fromDate,
        LocalDate toDate,
        boolean halfDay,
        @Schema(nullable = true) String halfDayPart,
        BigDecimal days,
        @Schema(nullable = true) String reason,
        String status,
        @Schema(nullable = true) CurrentStageDto currentStage,
        List<ApprovalStepDto> steps,
        @Schema(description = "Actions the calling user can take right now.") List<String> actions,
        LocalDateTime createdAt
) {}
