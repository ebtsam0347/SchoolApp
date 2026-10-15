package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.DisciplinaryAction;

public final class DisciplinaryActionDtoMapper {

    private DisciplinaryActionDtoMapper() {}

    public static DisciplinaryActionDto of(DisciplinaryAction d) {
        return new DisciplinaryActionDto(
                d.getId(), d.getEmployeeId(),
                d.getLevel(), d.getCategory(),
                d.getIncidentDate(), d.getIssuedOn(), d.getReason(),
                d.getDecision(), d.getDecisionNotes(), d.getClosedOn(),
                d.getStatus());
    }
}
