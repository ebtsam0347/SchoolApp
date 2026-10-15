package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.PayrollRunSkip;

public final class PayrollRunSkipDtoMapper {

    private PayrollRunSkipDtoMapper() {}

    public static PayrollRunSkipDto of(PayrollRunSkip s) {
        return new PayrollRunSkipDto(
                s.getId(),
                s.getEmployeeId(),
                s.getEmployeeCode(),
                s.getEmployeeFullName(),
                s.getErrorCode(),
                s.getMessage(),
                s.getCreatedAt());
    }
}
