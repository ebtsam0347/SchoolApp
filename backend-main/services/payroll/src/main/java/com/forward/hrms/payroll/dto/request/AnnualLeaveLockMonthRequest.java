package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "AnnualLeaveLockMonthRequest")
public record AnnualLeaveLockMonthRequest(
        @NotNull @Min(2026) @Max(9999)
        @Schema(description = "Calculation year to lock.", example = "2026")
        Integer year,
        @NotNull @Min(1) @Max(12)
        @Schema(description = "Calculation month to lock (1-12).", example = "10")
        Integer month
) {
}
