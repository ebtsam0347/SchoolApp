package com.forward.hrms.payroll.client.dto.attendace;

import java.math.BigDecimal;

/** Mirrors attendance-service's {@code LeaveTypeAvailedDto}. */
public record LeaveTypeAvailedDto(
        String code,
        String name,
        BigDecimal availedThisMonth,
        BigDecimal availedThisCycle
) {}
