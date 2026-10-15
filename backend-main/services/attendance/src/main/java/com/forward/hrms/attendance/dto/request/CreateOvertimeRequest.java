package com.forward.hrms.attendance.dto.request;

import com.forward.hrms.attendance.enums.OvertimeType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(name = "CreateOvertimeRequest",
        description = "Self-apply OT for a single working day. Authorization-only — no hours/minutes; " +
                "actual minutes are derived later from `attendance_records.worked_minutes`. The chosen " +
                "`overtimeType` selects the OT bucket credited: SPECIAL (Sunday / holiday only, whole worked day), " +
                "NORMAL (first two hours past shift end) or EXTRA (beyond that). File NORMAL and EXTRA " +
                "separately for the same day; one active request per type per day.")
public record CreateOvertimeRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate workDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Which OT bucket the day's overtime is credited to.")
        @NotNull OvertimeType overtimeType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"B34002", "B34003", "B34004", "B34005", "B34006", "B34007", "Administrator"},
                description = "Factory / unit the overtime is worked for.")
        @NotNull com.forward.hrms.attendance.enums.FactoryCode factoryCode,
        @Schema(nullable = true) String reason,
        @Schema(nullable = true,
                description = "Submit on behalf of another employee. Three authorities: the " +
                        "target's direct manager (MANAGER stage auto-approved, advances to HR); " +
                        "`attendance.approval.override` (HR — whole chain auto-approved); or " +
                        "`team.request.generation.overtime` for a target in the caller's own " +
                        "department (nothing auto-approved — the chain opens at the *caller's* " +
                        "manager, i.e. the department head they support). Anything else is " +
                        "403 ON_BEHALF_NOT_AUTHORIZED.")
        Long onBehalfOf
) {}
