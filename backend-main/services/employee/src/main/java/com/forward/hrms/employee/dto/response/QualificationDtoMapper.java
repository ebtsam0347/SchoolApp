package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Qualification;

public final class QualificationDtoMapper {

    private QualificationDtoMapper() {}

    public static QualificationDto of(Qualification q) {
        return new QualificationDto(q.getId(), q.getCode(), q.getName(), q.getLevel(),
                q.isActive(), q.getDisplayOrder());
    }
}
