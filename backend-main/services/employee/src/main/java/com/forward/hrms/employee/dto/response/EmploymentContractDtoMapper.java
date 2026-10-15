package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.EmploymentContract;

public final class EmploymentContractDtoMapper {

    private EmploymentContractDtoMapper() {}

    public static EmploymentContractDto of(EmploymentContract c) {
        return of(c, null);
    }

    public static EmploymentContractDto of(EmploymentContract c, String documentUrl) {
        return new EmploymentContractDto(
                c.getId(), c.getEmployeeId(), c.getContractType(),
                c.getStartDate(), c.getEndDate(), c.getStatus(),
                c.getProbationDays(), c.isExtended(), c.getDocumentKey(),
                documentUrl);
    }
}
