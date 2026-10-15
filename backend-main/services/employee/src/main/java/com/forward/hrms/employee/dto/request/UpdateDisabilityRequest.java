package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "UpdateDisabilityRequest",
        description = "HR-admin partial update. Null fields are unchanged. `code` is immutable.")
public record UpdateDisabilityRequest(
        @Schema(nullable = true) @Size(min = 1, max = 128) String name,
        @Schema(nullable = true) Boolean active
) {}
