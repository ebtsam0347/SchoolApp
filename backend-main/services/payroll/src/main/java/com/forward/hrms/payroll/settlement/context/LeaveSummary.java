package com.forward.hrms.payroll.settlement.context;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LeaveSummary(

        Long leaveTypeId,

        String leaveCode,

        String leaveName,

        LocalDate cycleStart,

        LocalDate cycleEnd,

        BigDecimal allocated,

        BigDecimal accrued,

        BigDecimal used,

        BigDecimal pending,

        BigDecimal payableDays,

        BigDecimal recoverableDays,

        BigDecimal encashmentAmount,

        BigDecimal recoveryAmount
) {
}
