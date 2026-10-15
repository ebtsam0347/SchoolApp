package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.SalaryComponent;

public final class SalaryComponentDtoMapper {

    private SalaryComponentDtoMapper() {}

    public static SalaryComponentDto of(SalaryComponent c) {
        return new SalaryComponentDto(c.getId(), c.getCode(), c.getName(), c.getType(),
                c.getCalcType(), c.getDefaultAmount(), c.getDisplayOrder(), c.isActive(), c.isCalculateWithSalary());
    }
}
