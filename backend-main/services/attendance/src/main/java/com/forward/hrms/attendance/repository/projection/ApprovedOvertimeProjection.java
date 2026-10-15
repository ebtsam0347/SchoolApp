package com.forward.hrms.attendance.repository.projection;

public interface ApprovedOvertimeProjection {

    Long getApprovedRequests();

    Integer getNormalMinutes();

    Integer getExtraMinutes();

    Integer getSpecialMinutes();

}