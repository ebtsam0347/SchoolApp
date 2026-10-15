package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.City;

public final class CityDtoMapper {

    private CityDtoMapper() {}

    public static CityDto of(City c) {
        return new CityDto(
                c.getId(), c.getCode(), c.getName(),
                c.getProvince().getId(), c.getProvince().getCode(),
                c.isActive(), c.getDisplayOrder());
    }
}
