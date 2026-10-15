package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.BonusNoSettingsStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "BonusNoSettingsDto")
public record BonusNoSettingsDto(
        long id,
        String fiscalYearCode,
        int bonusNo,
        BigDecimal percentageOfSalary,
        BonusNoSettingsStatus status
) {}
