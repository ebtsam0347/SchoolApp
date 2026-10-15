package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.EmployeeHistory;

public final class EmployeeHistoryDtoMapper {

    private EmployeeHistoryDtoMapper() {}

    public static EmployeeHistoryDto of(EmployeeHistory h) {
        return new EmployeeHistoryDto(
                h.getId(), h.getEmployeeId(),
                h.getEffectiveFrom(), h.getEffectiveTo(),
                h.getDepartmentId(), h.getSectionId(),
                h.getDesignationId(), h.getGradeId(),
                h.getManagerId(),
                h.getChangeReason(), h.getNotes());
    }
}
