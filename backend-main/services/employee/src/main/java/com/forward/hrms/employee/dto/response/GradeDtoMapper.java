package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Grade;

public final class GradeDtoMapper {

    private GradeDtoMapper() {}

    public static GradeDto of(Grade g) {
        return new GradeDto(g.getId(), g.getCode(), g.getName(), g.getLevel(),
                g.getDescription(), g.isActive(), g.getDisplayOrder(), g.getGradeType());
    }
}
