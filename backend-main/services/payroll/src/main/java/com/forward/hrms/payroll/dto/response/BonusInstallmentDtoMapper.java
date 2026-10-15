package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.BonusInstallment;

public final class BonusInstallmentDtoMapper {

    private BonusInstallmentDtoMapper() {}

    public static BonusInstallmentDto of(BonusInstallment i) {
        return new BonusInstallmentDto(i.getId(), i.getBonusEventId(), i.getInstallmentNo(),
                i.getPercentage(), i.getPaidOn(), i.getStatus(),
                i.getAppliedAt(), i.getAppliedBy());
    }
}
