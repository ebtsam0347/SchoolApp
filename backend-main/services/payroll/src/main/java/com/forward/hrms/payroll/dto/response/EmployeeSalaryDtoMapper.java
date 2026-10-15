package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.EmployeeSalary;

public final class EmployeeSalaryDtoMapper {

    private EmployeeSalaryDtoMapper() {}

    public static EmployeeSalaryDto of(EmployeeSalary s) {
        return new EmployeeSalaryDto(s.getId(), s.getEmployeeId(), s.getBasic(),
                s.getEffectiveFrom(), s.getEffectiveTo(), s.getRevisionReason());
    }
}
