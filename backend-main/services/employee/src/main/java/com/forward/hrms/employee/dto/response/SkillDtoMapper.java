package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Skill;

public final class SkillDtoMapper {

    private SkillDtoMapper() {}

    public static SkillDto of(Skill s) {
        return new SkillDto(s.getId(), s.getCode(), s.getName(), s.getCategory(), s.isActive());
    }
}
