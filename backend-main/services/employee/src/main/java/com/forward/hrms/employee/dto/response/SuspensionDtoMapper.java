package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.EmployeeSuspension;

public final class SuspensionDtoMapper {

    private SuspensionDtoMapper() {}

    public static SuspensionDto of(EmployeeSuspension s) {
        return new SuspensionDto(
                s.getId(), s.getEmployeeId(),
                s.getStartDate(), s.getEndDate(),
                s.getReason(), s.isWithPay(),
                s.getStatus(), s.getLiftedOn(), s.getLiftedReason());
    }
}
