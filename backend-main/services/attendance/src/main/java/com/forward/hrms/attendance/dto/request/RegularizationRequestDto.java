package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Schema(name = "RegularizationRequest",
        description = "Submit a regularization for a missed check-in / check-out. At least one of " +
                "`checkIn` / `checkOut` must be set; `reason` is mandatory. The request flows through " +
                "the same multi-stage approval chain as leaves and OT.")
public record RegularizationRequestDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate workDate,
        @Schema(nullable = true) LocalDateTime checkIn,
        @Schema(nullable = true) LocalDateTime checkOut,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String reason,
        @Schema(nullable = true,
                description = "Submit on behalf of another employee. Three authorities: the " +
                        "target's direct manager (MANAGER stage auto-approved, advances to HR); " +
                        "`attendance.approval.override` (HR — whole chain auto-approved); or " +
                        "`team.request.generation.regularization` for a target in the caller's own " +
                        "department (nothing auto-approved — the chain opens at the *caller's* " +
                        "manager, i.e. the department head they support). Anything else is " +
                        "403 ON_BEHALF_NOT_AUTHORIZED.")
        Long onBehalfOf
) {}
