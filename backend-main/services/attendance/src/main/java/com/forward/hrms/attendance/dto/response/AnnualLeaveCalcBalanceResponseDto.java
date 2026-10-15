package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AnnualLeaveCalcBalanceResponseDto(

        long id,
        long employeeId,
        long leaveTypeId,

        @Schema(description = "Calendar year the cycle starts in (grouping label).")
        int year,

        BigDecimal allocated,
        BigDecimal used,
        BigDecimal pending,
        BigDecimal carried,
        BigDecimal available,

        @Schema(description = "Inclusive first day of this entitlement cycle.")
        LocalDate cycleStart,
        @Schema(description = "Inclusive last day of this entitlement cycle.")
        LocalDate cycleEnd
) {

}
