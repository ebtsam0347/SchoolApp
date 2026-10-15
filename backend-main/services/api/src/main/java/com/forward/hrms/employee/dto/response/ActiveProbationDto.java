package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Compact view of the employee's currently active PROBATION contract.
 * Populated only on probation queries (the {@code probationDueWithin}
 * / {@code probationOverdue} filters and the per-employee detail
 * fetch); kept off the default DTO so list responses stay lean.
 */
@Schema(name = "ActiveProbationDto",
        description = "Per-row active-probation summary for HR's probation queue.")
public record ActiveProbationDto(
        @Schema(description = "Underlying employment_contracts row id.", example = "734")
        long contractId,
        @Schema(description = "Probation end date.", example = "2026-05-15")
        LocalDate endDate,
        @Schema(description = "Days remaining (negative when overdue).", example = "12")
        int daysRemaining,
        @Schema(description = "True iff the probation has already been extended once.")
        boolean isExtended
) {
    /** Pure helper — no entity dep, so it stays on the record in the api module. */
    public static ActiveProbationDto of(long contractId, LocalDate endDate, LocalDate today, boolean isExtended) {
        int days = (int) java.time.temporal.ChronoUnit.DAYS.between(today, endDate);
        return new ActiveProbationDto(contractId, endDate, days, isExtended);
    }
}
