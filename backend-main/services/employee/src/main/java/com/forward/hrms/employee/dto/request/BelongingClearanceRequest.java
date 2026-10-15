package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.belonging.ClearanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BelongingClearanceRequest(
        @NotNull
        ClearanceStatus clearanceStatus,

        @Size(max = 50)
        String clearanceId,

        @NotNull
        LocalDate clearanceDate
) {
}
