package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CurrentStageDto",
        description = "The PENDING stage of the chain. Null when the request is finalized.")
public record CurrentStageDto(
        int stageNo,
        String stageRole,
        @Schema(nullable = true) Long assignedTo,
        @Schema(nullable = true) String assignedToName
) {}
