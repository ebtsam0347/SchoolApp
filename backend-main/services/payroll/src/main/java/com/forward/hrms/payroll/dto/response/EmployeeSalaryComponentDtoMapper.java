package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.EmployeeSalaryComponent;
import com.forward.hrms.payroll.entity.SalaryComponent;

public final class EmployeeSalaryComponentDtoMapper {

    private EmployeeSalaryComponentDtoMapper() {}

    public static EmployeeSalaryComponentDto of(EmployeeSalaryComponent line, SalaryComponent comp) {
        return new EmployeeSalaryComponentDto(
                line.getId(), line.getEmployeeId(), line.getComponentId(),
                comp.getCode(), comp.getName(), comp.isCalculateWithSalary(), comp.getType(), comp.getCalcType(),
                line.getAmount(), line.getEffectiveFrom(), line.getEffectiveTo(), line.getNotes());
    }
}
