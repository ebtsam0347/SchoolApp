package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

@Schema(name = "NotCheckedInDto",
        description = "An active employee whose shift-start + grace has already elapsed today " +
                "without a check-in punch. Derived live at read time — nothing is persisted, and " +
                "the row drops off the list the moment a punch arrives. Distinct from the settled " +
                "ABSENT status the nightly close writes.")
public record NotCheckedInDto(
        @Schema(description = "Employee id (employee-service row id).") long employeeId,
        @Schema(description = "Employee code.") String employeeCode,
        @Schema(description = "Full name (from the attendance-side employee mirror).") String fullName,
        @Schema(description = "Resolved shift id for today; null on override-only days.") Long shiftId,
        @Schema(description = "Shift start time for today.") LocalTime shiftStartTime,
        @Schema(description = "Grace minutes applied to the shift.") int graceMinutes,
        @Schema(description = "Cutoff time after which a missing check-in counts (shiftStart + grace).")
        LocalTime cutoff,
        @Schema(description = "Minutes elapsed past the cutoff as of the query time.") long minutesOverdue
) {}
