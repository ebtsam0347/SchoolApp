package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Section;

public final class SectionDtoMapper {

    private SectionDtoMapper() {}

    public static SectionDto of(Section s) {
        return new SectionDto(
                s.getId(), s.getCode(), s.getName(),
                s.getDepartment().getId(), s.getDepartment().getCode(),
                s.getHeadId(), s.isActive());
    }
}
