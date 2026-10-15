package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.EmployeePayrollSettings;

public final class EmployeePayrollSettingsDtoMapper {

    private EmployeePayrollSettingsDtoMapper() {}

    public static EmployeePayrollSettingsDto of(EmployeePayrollSettings s) {
        return new EmployeePayrollSettingsDto(s.getId(), s.getEmployeeId(),
                s.getHireDate(), s.isEobiEnrolled(), s.getPaymentMode(),
                s.getGratuityStartDate(), s.getSeniorAllowanceStartDate());
    }
}
