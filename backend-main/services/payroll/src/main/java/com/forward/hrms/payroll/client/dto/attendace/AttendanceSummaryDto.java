package com.forward.hrms.payroll.client.dto.attendace;


public record AttendanceSummaryDto(

        long presentDays,

        long halfDays,

        long leaveDays,

        long paidLeaveDays,

        long unpaidLeaveDays,

        long absentDays,

        long weekendDays,

        long holidayDays

) {
}
