package com.forward.hrms.commons.approval.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "WorkflowStageDto", description = "One configured stage in an approval chain.")
public record WorkflowStageDto(
        @Schema(description = "1-based position in the chain.") int stageNo,
        @Schema(description = "MANAGER, PERMISSION, DEPARTMENT_HEAD, or GRADE.") String resolverKind,
        @Schema(nullable = true) String approvePermission,
        @Schema(nullable = true, description = "Department/grade code for DEPARTMENT_HEAD/GRADE stages.")
        String resolverTarget,
        @Schema(description = "Display label.") String label,
        boolean active
) {}
