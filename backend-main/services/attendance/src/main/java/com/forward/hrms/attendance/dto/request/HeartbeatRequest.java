package com.forward.hrms.attendance.dto.request;

import com.forward.hrms.attendance.enums.DeviceStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(name = "HeartbeatRequest",
        description = "Poller posts this on every cycle (success or fail). Drives the dashboard freshness " +
                "indicator. Optional fields are reported on first contact / firmware change so the " +
                "catalog stays current without an HR edit.")
public record HeartbeatRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull DeviceStatus status,
        @Schema(nullable = true) LocalDateTime deviceTime,
        @Schema(nullable = true) String firmwareVersion,
        @Schema(nullable = true) String serialNumber
) {}
