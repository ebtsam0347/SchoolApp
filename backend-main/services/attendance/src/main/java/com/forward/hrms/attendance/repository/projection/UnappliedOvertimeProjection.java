package com.forward.hrms.attendance.repository.projection;


public interface UnappliedOvertimeProjection {

    Long getAttendanceDays();

    Integer getNormalMinutes();

    Integer getExtraMinutes();

    Integer getSpecialMinutes();

}
