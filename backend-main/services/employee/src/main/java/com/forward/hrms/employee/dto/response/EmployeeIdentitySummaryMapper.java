package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Employee;

public final class EmployeeIdentitySummaryMapper {

    private EmployeeIdentitySummaryMapper() {}

    public static EmployeeIdentitySummary of(Employee e, String signedUrl) {
        return new EmployeeIdentitySummary(
                e.getUserId(),
                e.getId(),
                e.getFullName(),
                signedUrl,
                e.getProfilePictureKey());
    }
}
