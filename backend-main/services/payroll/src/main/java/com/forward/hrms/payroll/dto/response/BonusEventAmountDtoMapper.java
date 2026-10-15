package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.BonusEventAmount;

public final class BonusEventAmountDtoMapper {

    private BonusEventAmountDtoMapper() {}

    public static BonusEventAmountDto of(BonusEventAmount a) {
        return new BonusEventAmountDto(a.getId(), a.getBonusEventId(), a.getEmployeeId(),
                a.getHireDate(), a.getServiceDays(), a.getBasicSnapshot(), a.getFullBonus());
    }
}
