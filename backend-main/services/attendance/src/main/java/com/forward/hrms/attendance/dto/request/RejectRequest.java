package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(name = "RejectRequest", description = "Reason recorded on the approval step.")
public record RejectRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String reason
) {}
