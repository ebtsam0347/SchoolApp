package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.BonusEvent;

import java.util.List;

public final class BonusEventDtoMapper {

    private BonusEventDtoMapper() {}

    public static BonusEventDto of(BonusEvent e) {
        return of(e, null, null);
    }

    public static BonusEventDto of(BonusEvent e,
                                   List<BonusInstallmentDto> installments,
                                   List<BonusEventAmountDto> amounts) {
        return new BonusEventDto(e.getId(), e.getFiscalYearCode(), e.getName(),
                e.getEventType(), e.getReferenceDate(), e.getFullServiceDaysThreshold(),
                e.getStatus(), e.getNotes(), e.getActivatedAt(), e.getActivatedBy(),
                installments, amounts);
    }
}
