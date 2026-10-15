package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "OrgPulseDto",
        description = "Org-wide attendance roll-up for a single date. One SQL aggregate over " +
                "attendance_records — feeds the HR dashboard's `OrgPulse` card and avoids the " +
                "BFF pulling per-employee status rows just to bucket four numbers.")
public record OrgPulseDto(
        LocalDate asOf,
        long present,
        long late,
        long absent,
        long onLeave,
        long halfDay,
        long holiday,
        long weekend,
        long total
) {}
