package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LeanRemoveRequisitionDetailRequest",
        description = "Reason the Manufacturing Excellence reviewer struck this position off the requisition.")
public record LeanRemoveRequisitionDetailRequest(
        @Schema(description = "Why the position was removed during lean review.", maxLength = 64)
        @NotBlank @Size(max = 64) String leanRemovedRemarks
) {}
