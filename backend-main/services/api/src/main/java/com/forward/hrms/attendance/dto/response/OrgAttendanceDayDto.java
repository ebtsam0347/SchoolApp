package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "OrgAttendanceDayDto",
        description = "Per-day org-wide status counts. Used by the HR dashboard's 30-day trend " +
                "and any other surface that wants attendance volume over time without joining " +
                "across 312 × 30 rows on the FE.")
public record OrgAttendanceDayDto(
        LocalDate date,
        long present,
        long late,
        long absent,
        long onLeave,
        long halfDay,
        long total
) {}
