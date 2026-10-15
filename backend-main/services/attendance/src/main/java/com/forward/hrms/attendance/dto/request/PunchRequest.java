package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "PunchRequest",
        description = "ESS self-punch. `at` is optional — when omitted, the server uses now().")
public record PunchRequest(
        @Schema(description = "Punch instant. Server stamps now() when null.", nullable = true)
        LocalDateTime at
) {}
