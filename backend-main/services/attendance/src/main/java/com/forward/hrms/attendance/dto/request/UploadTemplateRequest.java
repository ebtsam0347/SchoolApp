package com.forward.hrms.attendance.dto.request;

import com.forward.hrms.attendance.enums.TemplateKind;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UploadTemplateRequest",
        description = "Poller posts a captured template here. Store-only — the row is saved " +
                "centrally; pushing to devices is a separate, operator-chosen step.")
public record UploadTemplateRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull TemplateKind kind,
        @Schema(nullable = true, description = "0..9 for FINGERPRINT; must be null for FACE.")
        Short fingerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) short format,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Base64-encoded template bytes.")
        @NotBlank String bytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long capturedDeviceId
) {}
