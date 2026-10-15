package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(name = "DeletePunchRequest",
        description = "Operator hard-delete of a punch. Reason is required and goes onto the audit " +
                "trail; the row itself is removed and the day's attendance_records are recomputed.")
public record DeletePunchRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String reason
) {}
