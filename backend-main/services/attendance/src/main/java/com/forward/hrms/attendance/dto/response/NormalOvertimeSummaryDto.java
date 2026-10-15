package com.forward.hrms.attendance.dto.response;

/**
 * Normal-overtime-only breakdown for the termination settlement summary.
 * Extra and Special overtime are intentionally excluded here — this DTO
 * backs only {@code TerminationAttendanceSummaryDto}, not the general
 * overtime endpoints, which still report normal/extra/special.
 */
public record NormalOvertimeSummaryDto(

        DurationDto approved,

        DurationDto pending,

        DurationDto unapplied,

        DurationDto total

) {
}
