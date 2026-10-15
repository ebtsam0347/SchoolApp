package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Province;

public final class ProvinceDtoMapper {

    private ProvinceDtoMapper() {}

    public static ProvinceDto of(Province p) {
        return new ProvinceDto(p.getId(), p.getCode(), p.getName(), p.isActive());
    }
}
