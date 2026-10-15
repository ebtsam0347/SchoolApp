package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WorkflowStageDto", description = "One configured stage in a request type's approval chain.")
public record WorkflowStageDto(
        int stageNo,
        @Schema(description = "MANAGER (snapshots the submitter's manager) or PERMISSION (role-only pool).")
        String resolverKind,
        @Schema(nullable = true, description = "The permission a PERMISSION-kind stage gates on; null for MANAGER.")
        String approvePermission,
        @Schema(nullable = true, description = "Department/grade code for DEPARTMENT_HEAD/GRADE stages.")
        String resolverTarget,
        String label,
        boolean active
) {}
