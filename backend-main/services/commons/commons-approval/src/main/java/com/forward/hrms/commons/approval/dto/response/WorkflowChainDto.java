package com.forward.hrms.commons.approval.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "WorkflowChainDto", description = "The full ordered approval chain for one request type.")
public record WorkflowChainDto(
        @Schema(description = "Request type key, e.g. LEAVE, OVERTIME.") String requestType,
        @Schema(description = "Stages in stage_no order.") List<WorkflowStageDto> stages
) {}
