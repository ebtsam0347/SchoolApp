package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(name = "AmendPunchRequest",
        description = "Operator amendment to an existing punch. Both fields are mandatory — the new " +
                "instant and an audit-grade reason explaining the correction. Source / employee / " +
                "device stay as-is.")
public record AmendPunchRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDateTime punchedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Free-text justification (\"corrected per paper form #43, dial-pad lag\").")
        @NotBlank String reason
) {}
