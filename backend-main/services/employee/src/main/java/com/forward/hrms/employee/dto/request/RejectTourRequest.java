package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(name = "RejectTourRequest")
public record RejectTourRequest(
        @Schema(description = "Reason for rejection.") @NotBlank String reason
) {}
