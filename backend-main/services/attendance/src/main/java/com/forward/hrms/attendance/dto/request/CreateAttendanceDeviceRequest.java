package com.forward.hrms.attendance.dto.request;

import com.forward.hrms.attendance.enums.DeviceType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateAttendanceDeviceRequest",
        description = "HR-admin create payload for the biometric / kiosk device catalog. " +
                "`code` must be unique and is immutable post-create. The comm port is read by " +
                "the attendance-poller sidecar to open a ZK-protocol session — leave at the " +
                "default (4370) for stock ZKTeco units. The ZK comm password is set via the " +
                "dedicated POST /devices/{id}/comm-password endpoint and is never returned in any GET.")
public record CreateAttendanceDeviceRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "RND_RECEPTION")
        @NotBlank @Size(min = 1, max = 32) String code,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Reception kiosk")
        @NotBlank @Size(min = 1, max = 128) String name,

        @Schema(nullable = true) @Size(max = 128) String location,

        @Schema(nullable = true, description = "IPv4 / IPv6 the poller dials.", example = "10.10.20.5")
        @Size(max = 64) String deviceIp,

        @Schema(nullable = true, description = "Default 4370.", example = "4370")
        @Min(1) @Max(65535) Integer commPort,

        @Schema(nullable = true,
                description = "True for the single device HR uses for in-person enrollment. Default false.")
        Boolean enrollment,

        @Schema(nullable = true,
                description = "Physical role of the device. Default ATTENDANCE — set to CANTEEN " +
                        "or ACCESS_LOG for units whose punches shouldn't count as attendance.")
        DeviceType deviceType
) {}
