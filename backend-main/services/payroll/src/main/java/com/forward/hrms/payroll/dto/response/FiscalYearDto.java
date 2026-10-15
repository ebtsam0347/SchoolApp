package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.FiscalYear;
import com.forward.hrms.payroll.enums.FiscalYearStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "FiscalYearDto")
public record FiscalYearDto(
        @Schema(example = "2025-26") String code,
        LocalDate startDate,
        LocalDate endDate,
        FiscalYearStatus status
) {
    public static FiscalYearDto of(FiscalYear fy) {
        return new FiscalYearDto(fy.getCode(), fy.getStartDate(), fy.getEndDate(), fy.getStatus());
    }
}
