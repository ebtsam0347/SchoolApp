package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.attendance.entity.AttendanceDevice;
import com.forward.hrms.attendance.enums.DeviceStatus;
import com.forward.hrms.attendance.enums.DeviceType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "AttendanceDeviceDto")
public record AttendanceDeviceDto(
        long id,
        String code,
        String name,
        @Schema(nullable = true) String location,
        @Schema(nullable = true, description = "IP address the poller dials in to.") String deviceIp,
        @Schema(description = "ZK protocol TCP port; default 4370.") int commPort,
        @Schema(description = "True when a non-zero ZK comm password has been set. Use POST /devices/{id}/comm-password to set it.")
        boolean commPasswordSet,
        @Schema(description = "True for the single device HR uses to enroll fingerprints / faces.")
        boolean enrollment,
        @Schema(description = "Physical role of the device. Default ATTENDANCE; flip to CANTEEN " +
                "or ACCESS_LOG for units whose punches shouldn't count as attendance.")
        DeviceType deviceType,
        @Schema(description = "Last reported by the poller's heartbeat.") DeviceStatus status,
        @Schema(nullable = true) LocalDateTime lastSeenAt,
        @Schema(nullable = true,
                description = "High-water-mark for biometric ingest. Null when no punch has been " +
                        "received yet or after POST /devices/{id}/reset-hwm.")
        LocalDateTime lastPunchedAt,
        @Schema(description = "Number of unmatched-card punches waiting in the replay queue.")
        long unmatchedCount,
        boolean active
) {
    public static AttendanceDeviceDto of(AttendanceDevice d, LocalDateTime lastPunchedAt, long unmatchedCount) {
        return new AttendanceDeviceDto(
                d.getId(), d.getCode(), d.getName(), d.getLocation(), d.getDeviceIp(),
                d.getCommPort(), d.getCommPassword() != 0, d.isEnrollment(),
                d.getDeviceType(), d.getStatus(), d.getLastSeenAt(),
                lastPunchedAt, unmatchedCount, d.isActive());
    }
}
