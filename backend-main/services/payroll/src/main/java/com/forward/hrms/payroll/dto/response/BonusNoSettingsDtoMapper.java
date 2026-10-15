package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.BonusNoSettings;

public final class BonusNoSettingsDtoMapper {

    private BonusNoSettingsDtoMapper() {}

    public static BonusNoSettingsDto of(BonusNoSettings s) {
        return new BonusNoSettingsDto(
                s.getId(),
                s.getFiscalYearCode(),
                s.getBonusNo(),
                s.getPercentageOfSalary(),
                s.getStatus());
    }
}
