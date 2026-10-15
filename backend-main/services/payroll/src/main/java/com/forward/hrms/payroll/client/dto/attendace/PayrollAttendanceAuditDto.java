package com.forward.hrms.payroll.client.dto.attendace;

import java.math.BigDecimal;

/** Mirrors attendance-service's {@code PayrollAttendanceAuditDto}. */
public record PayrollAttendanceAuditDto(
        int calendarDays,
        int preJoiningDays,
        int weeklyOffDays,
        int holidayDays,
        int workingDays,
        BigDecimal presentDays,
        BigDecimal paidLeaveDays,
        BigDecimal unpaidLeaveDays,
        BigDecimal absentDays,
        BigDecimal unrecordedAbsentDays,
        int halfDayRecords,
        int presentOnOffDays,
        int leaveConflictDays,
        BigDecimal lopDays,
        BigDecimal paidDays
) {}
