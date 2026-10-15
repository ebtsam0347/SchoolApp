package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.entity.DeviceSnapshot;
import com.forward.hrms.attendance.enums.DeviceSnapshotStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "DeviceSnapshotSummaryDto",
        description = "Lightweight view of a device snapshot — status + counts + error, no payloads. " +
                "Use the per-id GET to fetch the full deviceInfo / users / attendance JSON.")
public record DeviceSnapshotSummaryDto(
        long id,
        long deviceId,
        @Schema(nullable = true) Long syncTaskId,
        DeviceSnapshotStatus status,
        String requestedBy,
        LocalDateTime requestedAt,
        @Schema(nullable = true) LocalDateTime completedAt,
        @Schema(nullable = true) Integer userCount,
        @Schema(nullable = true) Integer recordCount,
        @Schema(nullable = true) String error
) {
    public static DeviceSnapshotSummaryDto of(DeviceSnapshot s) {
        return new DeviceSnapshotSummaryDto(
                s.getId(), s.getDeviceId(), s.getSyncTaskId(), s.getStatus(),
                s.getRequestedBy(), s.getCreatedAt(), s.getCompletedAt(),
                s.getUserCount(), s.getRecordCount(), s.getError());
    }
}
