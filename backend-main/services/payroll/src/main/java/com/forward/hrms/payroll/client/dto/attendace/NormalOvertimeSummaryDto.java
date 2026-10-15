package com.forward.hrms.payroll.client.dto.attendace;

/**
 * Normal-overtime-only breakdown returned by attendance-service's
 * termination-summary endpoint. Extra and Special overtime are excluded
 * from this snapshot on purpose — see attendance-service's
 * {@code NormalOvertimeSummaryDto} for the source shape.
 */
public record NormalOvertimeSummaryDto(

        DurationDto approved,

        DurationDto pending,

        DurationDto unapplied,

        DurationDto total

) {
}
