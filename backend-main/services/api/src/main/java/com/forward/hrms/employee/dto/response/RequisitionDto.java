package com.forward.hrms.employee.dto.response;

import com.forward.hrms.attendance.dto.response.ApprovalStepDto;
import com.forward.hrms.attendance.dto.response.CurrentStageDto;
import com.forward.hrms.employee.enums.RequisitionStatus;
import com.forward.hrms.employee.enums.RequisitionType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "RequisitionDto")
public record RequisitionDto(
        long id,
        @Schema(description = "NEW_HIRE or REPLACEMENT.") RequisitionType type,
        @Schema(description = "Current workflow status.") RequisitionStatus status,
        Long departmentId,
        String minimumEducation,
        Long minimumExperience,
        String minimumSkill,
        String jobDescription,
        Long totalPositionsHod,
        Long totalPositionLean,
        @Schema(description = "Detail rows filled with a real employee code.") long newEmployeeCodeCount,
        @Schema(description = "Detail rows still carrying only a temporary reference.") long temporaryEmployeeReferenceCount,
        String requestReason,
        String hotNotes,


        Long requestedByUserId,
        @Schema(nullable = true) Long approvedByUserId,
        @Schema(nullable = true) LocalDateTime approvedAt,
        @Schema(nullable = true) String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String createdBy,
        @Schema(nullable = true) CurrentStageDto currentStage,
        List<ApprovalStepDto> steps,
        @Schema(description = "Actions the calling user can take right now.") List<String> actions
) {}
