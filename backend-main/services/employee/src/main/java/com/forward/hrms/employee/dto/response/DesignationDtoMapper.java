package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Designation;

public final class DesignationDtoMapper {

    private DesignationDtoMapper() {}

    public static DesignationDto of(Designation d) {
        return new DesignationDto(d.getId(), d.getCode(), d.getTitle(), d.getCategory(), d.isActive());
    }
}
