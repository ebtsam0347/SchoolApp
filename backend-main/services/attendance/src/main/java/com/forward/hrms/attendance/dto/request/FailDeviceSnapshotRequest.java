package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "FailDeviceSnapshotRequest",
        description = "Posted by the poller when a SNAPSHOT_DEVICE task can't be fulfilled — " +
                "device unreachable, ZK error, or anything else that means there is no payload " +
                "to deliver. Mirrors the sync task fail call but flips the snapshot row too.")
public record FailDeviceSnapshotRequest(
        @Schema(description = "Human-readable failure reason.",
                example = "device unreachable: TimeoutError",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 1000)
        String error,

        @Schema(description = "Whether the underlying sync task should remain retryable. " +
                "Default true — service applies the standard backoff. Pass false for " +
                "permanent failures (firmware mismatch, etc.).",
                example = "true")
        Boolean retryable
) {}
