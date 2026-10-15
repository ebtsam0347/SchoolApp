package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "ResignRequest")
public record ResignRequest(
        @Schema(description = "Last working day; must be in the future.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate lastWorkingDay,

        @Schema(description = "Short categorisation.", maxLength = 64)
        @Size(max = 64) String reason,

        @Schema(description = "Free-text notes for HR.")
        String notes,

        @Schema(description = "If the employee has direct reports, the id of the employee they " +
                "should be reassigned to. Required when reports exist.", nullable = true)
        Long reassignReportsTo
) {}
