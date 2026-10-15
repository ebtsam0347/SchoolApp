package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Religion;

public final class ReligionDtoMapper {

    private ReligionDtoMapper() {
    }

    public static ReligionDto of(Religion religion) {
        return new ReligionDto(
                religion.getId(),
                religion.getName(),
                religion.getActive()
        );
    }
}