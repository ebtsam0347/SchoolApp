package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(name = "AutoAttendanceRequest",
        description = "Operator fills one employee's attendance over a date range: an IN and an OUT " +
                "punch on every day from `from` to `to` (inclusive, max 62 days). Times default to " +
                "each day's own shift start / end when left out. A given time is placed on the " +
                "calendar date the shift window implies (opens 2h before the shift start), so an " +
                "18:00-02:00 shift's out is the next morning but counts for that work date.")
public record AutoAttendanceRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate from,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate to,
        @Schema(nullable = true, type = "string", example = "09:00",
                description = "Null = each day's shift start.")
        LocalTime inTime,
        @Schema(nullable = true, type = "string", example = "17:00",
                description = "Null = each day's shift end.")
        LocalTime outTime,
        @Schema(description = "Also fill Sundays and holidays. Default false — they are skipped.")
        Boolean includeOffDays,
        @Schema(description = "Also fill days that already have punches — the new IN / OUT are " +
                "pinned as the day's check-in / check-out, existing punches stay in the log. " +
                "Default false — those days are skipped.")
        Boolean replaceExisting,
        @Schema(nullable = true, description = "Operator's remark, appended to the narration.")
        @Size(max = 300) String remarks
) {}
