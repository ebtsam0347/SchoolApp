package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Polling target returned to the attendance-poller sidecar. Includes the
 * ZK comm password — that's why this endpoint is gated to
 * {@code attendance.poller} and is not the same shape as the public
 * {@link AttendanceDeviceDto}. The poller uses {@code lastPunchedAt} as
 * the high-water-mark; the service advances it on the next ingest.
 */
@Schema(name = "PollerTargetDto",
        description = "Active device the attendance-poller should pull from. Carries comm password — " +
                "do not surface this DTO outside the attendance.poller surface.")
public record PollerTargetDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ZK protocol IP.")
        String deviceIp,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int commPort,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "ZK comm password — sensitive, gate to attendance.poller.")
        int commPassword,
        @Schema(nullable = true,
                description = "High-water-mark; the poller filters its ZK pull to records strictly after this. " +
                        "Null on a fresh device — pull everything.")
        LocalDateTime lastPunchedAt
) {
}
