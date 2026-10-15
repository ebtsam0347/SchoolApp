package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "WorkflowChainDto", description = "The ordered approval chain configured for a request type.")
public record WorkflowChainDto(
        String requestType,
        List<WorkflowStageDto> stages
) {}
