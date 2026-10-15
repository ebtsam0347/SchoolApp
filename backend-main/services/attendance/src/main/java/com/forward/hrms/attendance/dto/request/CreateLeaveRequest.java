package com.forward.hrms.attendance.dto.request;

import com.forward.hrms.attendance.enums.HalfDayPart;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(name = "CreateLeaveRequest",
        description = "ESS leave application. Sundays + holidays in the range are skipped at submit time.")
public record CreateLeaveRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long leaveTypeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate fromDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate toDate,
        @Schema(description = "True for half-day requests; combine with `halfDayPart`.") boolean halfDay,
        @Schema(nullable = true) HalfDayPart halfDayPart,
        @Schema(nullable = true) String reason,
        @Schema(nullable = true,
                description = "Submit on behalf of another employee. Three authorities: the " +
                        "target's direct manager (MANAGER stage auto-approved, advances to HR); " +
                        "`attendance.approval.override` (HR — whole chain auto-approved); or " +
                        "`team.request.generation.leave` for a target in the caller's own " +
                        "department (nothing auto-approved — the chain opens at the *caller's* " +
                        "manager, i.e. the department head they support). Anything else is " +
                        "403 ON_BEHALF_NOT_AUTHORIZED.")
        Long onBehalfOf
) {}
