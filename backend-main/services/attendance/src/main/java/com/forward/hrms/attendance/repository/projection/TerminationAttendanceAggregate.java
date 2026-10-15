package com.forward.hrms.attendance.repository.projection;


public interface TerminationAttendanceAggregate {

    Long getPresentDays();

    Long getHalfDays();

    Long getLeaveDays();

    Long getPaidLeaveDays();

    Long getUnpaidLeaveDays();

    Long getAbsentDays();

    Long getWeekendDays();

    Long getHolidayDays();
}
