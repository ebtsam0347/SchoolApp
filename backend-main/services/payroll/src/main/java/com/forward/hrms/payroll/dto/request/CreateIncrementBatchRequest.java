package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "CreateIncrementBatchRequest",
        description = "JSON form for ad-hoc 1-line batches (promotion / retention) and direct uploads.")
public record CreateIncrementBatchRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull LocalDate effectiveFrom,
        String notes,
        @NotEmpty @Valid List<Line> lines
) {
    @Schema(name = "CreateIncrementBatchRequest.Line")
    public record Line(
            @NotNull Long employeeId,
            @NotNull @PositiveOrZero BigDecimal newBasic,
            @PositiveOrZero BigDecimal newIncomeTax,
            LocalDate effectiveFrom,
            String notes
    ) {}
}
