package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Per-letter overrides for the appointment letter. Every field is optional;
 * a null falls back to the employee record or {@code letter_settings}.
 * Bound from query parameters on GET and from the JSON body on POST.
 */
@Schema(name = "AppointmentLetterRequest",
        description = "Optional overrides for one appointment letter. Omitted fields use employee data or letter settings.")
public record AppointmentLetterRequest(
        @Schema(description = "Date printed on the letter. Defaults to today.", example = "2026-09-25")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate letterDate,

        @Schema(description = "Date of joining printed on the letter. Defaults to the employee's joining date.",
                example = "2026-10-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportingDate,

        @Schema(description = "Probation length in months. Defaults to the active probation contract, else the policy default.",
                example = "3", minimum = "0", maximum = "24")
        @Min(0) @Max(24) Integer probationMonths,

        @Schema(description = "Print basic salary and allowances. Defaults to true; requires `payroll.read` when true.",
                defaultValue = "true")
        Boolean includeSalary,

        @Schema(description = "Reference number. Defaults to {refPrefix}/APPT/{year}/{employeeCode}.",
                maxLength = 64, example = "FS/HR/APPT/2026/FS-2026-0042")
        @Size(max = 64) String refNo,

        @Schema(description = "Branch printed in Official Information. Defaults to the letter settings branch.",
                maxLength = 128, example = "Main Branch (Sahowala)")
        @Size(max = 128) String branch,

        @Schema(description = "Free-text \"Any Other Info\" line in Official Information. Blank by default.",
                maxLength = 300)
        @Size(max = 300) String otherInfo
) {

    public static AppointmentLetterRequest defaults() {
        return new AppointmentLetterRequest(null, null, null, null, null, null, null);
    }

    public boolean salaryIncluded() {
        return includeSalary == null || includeSalary;
    }
}
