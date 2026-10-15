package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(name = "FailSyncTaskRequest",
        description = "Reported by the poller when a sync task failed against the device. retryable=false " +
                "(e.g. template format mismatch, unknown user) parks the row in FAILED immediately " +
                "regardless of attempts; retryable=true (the default) increments attempts and re-arms " +
                "with exponential backoff until max-attempts is hit.")
public record FailSyncTaskRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String error,
        @Schema(nullable = true, description = "Defaults to true.") Boolean retryable
) {}
