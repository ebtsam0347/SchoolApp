package com.forward.hrms.attendance.repository.projection;

import java.time.LocalDate;

public interface ApprovedLeaveDayProjection {

    Long getEmployeeId();

    LocalDate getAttendanceDate();

    Long getLeaveTypeId();

    Boolean getHalfDay();
}
