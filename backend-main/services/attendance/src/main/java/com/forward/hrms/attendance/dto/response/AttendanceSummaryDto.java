package com.forward.hrms.attendance.dto.response;


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
