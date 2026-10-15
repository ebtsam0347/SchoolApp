package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

// deviceInfo / users / attendance are plain Object (free-form JSON), not a
// Jackson 2 JsonNode: SB4 deserializes the request body with Jackson 3, which
// can't bind a JSON object into a com.fasterxml JsonNode — it would land null
// and the stored snapshot payload would be empty. Object binds to Map/List
// natively, and the service's Jackson 2 ObjectMapper re-serializes a Map fine.
@Schema(name = "FinalizeDeviceSnapshotRequest",
        description = "Posted by the poller when a SNAPSHOT_DEVICE task completes. Carries the " +
                "captured deviceInfo / users / attendance JSON straight from the device. The " +
                "service stores them on the snapshot row and flips its status to COMPLETED.")
public record FinalizeDeviceSnapshotRequest(
        @Schema(description = "Device metadata — firmware, serial, platform, clock, etc.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Object deviceInfo,

        @Schema(description = "Shape: {count, users: [...]}.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Object users,

        @Schema(description = "Shape: {count, records: [...]}.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Object attendance,

        @Schema(description = "Convenience count of users in the dump (mirrors users.count).",
                example = "1240")
        Integer userCount,

        @Schema(description = "Convenience count of attendance records (mirrors attendance.count).",
                example = "8423")
        Integer recordCount
) {}
