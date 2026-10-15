package com.forward.hrms.payroll.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jetbrains.annotations.NotNull;

public record AnnualLeaveSearchRequest(

        @NotNull
        @Min(2025)
        Integer year,

        @NotNull
        @Min(1)
        @Max(12)
        Integer month,

        String employeeCode,

        /** true = paid, false = marked unpaid. Ignored when paymentPending is true. */
        Boolean paymentStatus,

        /** true = only rows with no payment decision yet (null paymentStatus). */
        Boolean paymentPending,

        /** true = locked, false = not locked (includes never-locked null rows). */
        Boolean lockStatus
) {
}