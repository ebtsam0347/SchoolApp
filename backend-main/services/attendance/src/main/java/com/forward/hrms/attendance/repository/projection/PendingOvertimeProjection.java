package com.forward.hrms.attendance.repository.projection;


public interface PendingOvertimeProjection {

    Long getPendingRequests();

    Integer getNormalMinutes();

    Integer getExtraMinutes();

    Integer getSpecialMinutes();
}