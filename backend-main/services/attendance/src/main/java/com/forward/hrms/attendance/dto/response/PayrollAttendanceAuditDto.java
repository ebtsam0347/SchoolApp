package com.forward.hrms.attendance.dto.response;

import java.math.BigDecimal;

/**
 * Per-employee attendance classification for one payroll period, derived
 * from the raw attendance records, the holiday calendar, approved leave
 * and the employee's joining date — used to <em>validate</em> what payroll
 * persisted, never to replace it.
 *
 * <p>Organisation rules encoded here (see {@code PayrollAttendanceClassifier}):
 * the weekly off is Sunday; a public holiday is a paid non-working day;
 * a day before the joining date is outside the employee's window.
 */
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
