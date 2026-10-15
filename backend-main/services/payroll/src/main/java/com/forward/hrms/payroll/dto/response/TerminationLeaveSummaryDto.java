package com.forward.hrms.payroll.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TerminationLeaveSummaryDto(

        Long leaveTypeId,

        String leaveCode,

        String leaveName,

        boolean encashable,

        LocalDate cycleStart,

        LocalDate cycleEnd,

        BigDecimal allocated,

        BigDecimal earned,

        BigDecimal used,

        BigDecimal pending,

        BigDecimal payableDays,

        BigDecimal recoverableDays,

        BigDecimal encashmentAmount,

        BigDecimal recoveryAmount
) {
}