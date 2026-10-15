package com.forward.hrms.attendance.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forward.hrms.attendance.entity.DeviceSnapshot;
import com.forward.hrms.attendance.enums.DeviceSnapshotStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "DeviceSnapshotDto",
        description = "Full device snapshot including deviceInfo / users / attendance JSON payloads. " +
                "All three are populated only when status is COMPLETED.")
public record DeviceSnapshotDto(
        long id,
        long deviceId,
        @Schema(nullable = true) Long syncTaskId,
        DeviceSnapshotStatus status,
        String requestedBy,
        LocalDateTime requestedAt,
        @Schema(nullable = true) LocalDateTime completedAt,
        @Schema(nullable = true) Integer userCount,
        @Schema(nullable = true) Integer recordCount,
        @Schema(nullable = true) String error,
        @Schema(nullable = true,
                description = "Raw device metadata as the poller captured it (firmware, serial, " +
                        "platform, clock, etc.). Shape varies by firmware.")
        Object deviceInfo,
        @Schema(nullable = true,
                description = "Users on the device. Shape: {count, users: [...]}.")
        Object users,
        @Schema(nullable = true,
                description = "Attendance log on the device at snapshot time. Shape: " +
                        "{count, records: [...]}.")
        Object attendance
) {
    public static DeviceSnapshotDto of(DeviceSnapshot s, ObjectMapper json) {
        return new DeviceSnapshotDto(
                s.getId(), s.getDeviceId(), s.getSyncTaskId(), s.getStatus(),
                s.getRequestedBy(), s.getCreatedAt(), s.getCompletedAt(),
                s.getUserCount(), s.getRecordCount(), s.getError(),
                read(json, s.getDeviceInfo()), read(json, s.getUsers()), read(json, s.getAttendance()));
    }

    // Object (free-form JSON), parsed by the Jackson 2 mapper but handed back
    // to SB4's Jackson 3 response serializer — a Jackson 2 JsonNode here would
    // serialize as bean junk. A Map/List round-trips through both.
    private static Object read(ObjectMapper json, String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return json.readValue(raw, Object.class);
        } catch (Exception e) {
            throw new IllegalStateException("corrupt snapshot payload", e);
        }
    }
}
