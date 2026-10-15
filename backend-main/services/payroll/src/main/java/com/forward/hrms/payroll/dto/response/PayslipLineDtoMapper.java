package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.PayslipLine;

public final class PayslipLineDtoMapper {

    private PayslipLineDtoMapper() {}

    public static PayslipLineDto of(PayslipLine l) {
        return new PayslipLineDto(l.getId(), l.getComponentId(), l.getCode(), l.getName(),
                l.getType(), l.getCalcType(), l.getRate(), l.getDays(), l.getAmount(),
                l.getDisplayOrder());
    }
}
