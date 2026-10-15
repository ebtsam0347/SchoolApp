package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Department;

public final class DepartmentDtoMapper {

    private DepartmentDtoMapper() {}

    public static DepartmentDto of(Department d) {
        return new DepartmentDto(d.getId(), d.getCode(), d.getName(), d.getHeadId(), d.isActive(), d.isProduction());
    }
}
