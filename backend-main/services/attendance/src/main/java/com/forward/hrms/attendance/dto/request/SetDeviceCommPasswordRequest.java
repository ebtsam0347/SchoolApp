package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "SetDeviceCommPasswordRequest",
        description = "Write-only set of the ZK comm password. The current value is never " +
                "returned in any GET — the device DTO instead exposes a commPasswordSet boolean.")
public record SetDeviceCommPasswordRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Numeric ZK comm password. Use 0 to clear.",
                example = "0")
        @NotNull @Min(0) @Max(999999) Integer commPassword
) {}
