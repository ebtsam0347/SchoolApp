package com.forward.hrms.attendance.dto.request;

import com.forward.hrms.attendance.enums.DeviceType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateAttendanceDeviceRequest",
        description = "HR-admin partial update. Any field left null is unchanged. `code` is " +
                "immutable; to retire a device flip `active=false` (DELETE). The ZK comm " +
                "password is set via the dedicated POST /devices/{id}/comm-password endpoint, " +
                "not this update path, so it cannot be exfiltrated through a leaked GET response.")
public record UpdateAttendanceDeviceRequest(
        @Schema(nullable = true) @Size(min = 1, max = 128) String name,
        @Schema(nullable = true) @Size(max = 128) String location,
        @Schema(nullable = true) @Size(max = 64) String deviceIp,
        @Schema(nullable = true) @Min(1) @Max(65535) Integer commPort,
        @Schema(nullable = true) Boolean enrollment,
        @Schema(nullable = true,
                description = "Reclassify this device. Most are ATTENDANCE; flip to CANTEEN " +
                        "or ACCESS_LOG for non-attendance units.")
        DeviceType deviceType,
        @Schema(nullable = true) Boolean active
) {}
