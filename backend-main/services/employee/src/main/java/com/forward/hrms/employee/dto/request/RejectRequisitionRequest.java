package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(name = "RejectRequisitionRequest")
public record RejectRequisitionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Reason for rejection — visible to the requester.")
        @NotBlank String reason
) {}
