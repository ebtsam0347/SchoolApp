package com.forward.hrms.payroll.dto.response;

public record AnnualLeaveLockMonthResponse(
        Integer year,
        Integer month,
        long lockedCount
) {}
