package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(name = "CreateShortLeaveRequest",
        description = "Apply for a sub-day leave: leave work between (startTime, endTime) on " +
                "leaveDate. Total minutes is derived from the window. Different from " +
                "regularization (which inserts missing punches) and from leave_requests " +
                "(full or half day).")
public record CreateShortLeaveRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate leaveDate,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "13:00")
        @NotNull LocalTime startTime,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "15:00",
                description = "Must be strictly greater than startTime — no midnight wrap.")
        @NotNull LocalTime endTime,

        @Schema(nullable = true) @Size(max = 1024) String reason,

        @Schema(nullable = true,
                description = "Submit on behalf of another employee. Three authorities: the " +
                        "target's direct manager (MANAGER stage auto-approved, advances to HR); " +
                        "`attendance.approval.override` (HR — whole chain auto-approved); or " +
                        "`team.request.generation.shortLeave` for a target in the caller's own " +
                        "department (nothing auto-approved — the chain opens at the *caller's* " +
                        "manager, i.e. the department head they support). Anything else is " +
                        "403 ON_BEHALF_NOT_AUTHORIZED.")
        Long onBehalfOf
) {}
