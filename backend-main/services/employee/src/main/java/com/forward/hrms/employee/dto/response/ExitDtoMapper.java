package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.EmployeeExit;

public final class ExitDtoMapper {

    private ExitDtoMapper() {}

    public static ExitDto of(EmployeeExit ex) {
        return new ExitDto(
                ex.getId(), ex.getEmployeeId(), ex.getExitType(),
                ex.getNoticeStartDate(), ex.getLastWorkingDay(),
                ex.getReason(), ex.getNotes(), ex.getStatus(),
                ex.getCompletedAt(), ex.getWithdrawnAt(), ex.getWithdrawnReason());
    }
}
